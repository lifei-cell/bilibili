package com.gary.bilibili.canal.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.canal.document.VideoDocument;
import com.gary.bilibili.canal.message.CacheSyncEvent;
import com.gary.bilibili.canal.repository.VideoDocumentRepository;
import com.gary.bilibili.canal.service.VideoBloomFilter;
import com.gary.bilibili.canal.service.VideoIndexWriteGate;
import com.gary.bilibili.canal.service.MySqlNamedLock;
import com.gary.bilibili.canal.service.PublishedVideoSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CacheSyncConsumerTest {

    private VideoDocumentRepository videoDocumentRepository;
    private StringRedisTemplate stringRedisTemplate;
    private SetOperations<String, String> setOperations;
    private VideoBloomFilter videoBloomFilter;
    private ReliableMessageExecutor reliableMessageExecutor;
    private MySqlNamedLock distributedLock;
    private PublishedVideoSource publishedVideoSource;
    private CacheSyncConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        videoDocumentRepository = mock(VideoDocumentRepository.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        videoBloomFilter = mock(VideoBloomFilter.class);
        reliableMessageExecutor = mock(ReliableMessageExecutor.class);
        distributedLock = mock(MySqlNamedLock.class);
        publishedVideoSource = mock(PublishedVideoSource.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(4).run();
            return null;
        }).when(reliableMessageExecutor).execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            invocation.<java.util.function.Supplier<?>>getArgument(2).get();
            return null;
        }).when(distributedLock).execute(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(java.util.function.Supplier.class));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        consumer = new CacheSyncConsumer(
                videoDocumentRepository, stringRedisTemplate, videoBloomFilter,
                reliableMessageExecutor, new VideoIndexWriteGate(), distributedLock,
                publishedVideoSource);
    }

    @Test
    void shouldIndexPublishedVideoAndRefreshBloomFilter() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("id", "10001");
        data.put("user_id", "1");
        data.put("title", "SpringBoot 视频平台实战");
        data.put("description", "从零实现视频平台后端");
        data.put("tags", "SpringBoot,Vue3");
        data.put("category_id", "7");
        data.put("status", "1");
        data.put("deleted", "0");
        data.put("create_time", "2026-08-03 10:00:00");
        VideoDocument current = new VideoDocument();
        current.setId(10001L);
        current.setTitle("当前数据库标题");
        current.setTags(java.util.List.of("SpringBoot", "Vue3"));
        current.setCreateTime("2026-08-03T10:00:00");
        when(publishedVideoSource.findById(10001L)).thenReturn(Optional.of(current));

        consumer.onMessage(event("video", "UPDATE", data));

        ArgumentCaptor<VideoDocument> captor = ArgumentCaptor.forClass(VideoDocument.class);
        verify(videoDocumentRepository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(document -> {
            assertThat(document.getId()).isEqualTo(10001L);
            assertThat(document.getTitle()).isEqualTo("当前数据库标题");
            assertThat(document.getTags()).containsExactly("SpringBoot", "Vue3");
            assertThat(document.getCreateTime()).isEqualTo("2026-08-03T10:00:00");
        });
        verify(stringRedisTemplate).delete("video:detail:10001");
        verify(videoBloomFilter).put(10001L);
        verify(distributedLock).execute(eq(VideoIndexWriteGate.DISTRIBUTED_LOCK_NAME), eq(30),
                any(java.util.function.Supplier.class));
    }

    @Test
    void shouldRemoveOfflineVideoFromIndex() {
        when(publishedVideoSource.findById(10001L)).thenReturn(Optional.empty());
        consumer.onMessage(event("video", "UPDATE", Map.of(
                "id", "10001",
                "status", "3",
                "deleted", "0")));

        verify(videoDocumentRepository).deleteById(10001L);
        verify(videoDocumentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRefreshIndexedStatsAfterDatabaseSync() {
        VideoDocument document = new VideoDocument();
        document.setId(10001L);
        document.setViewCount(1200L);
        document.setLikeCount(88L);
        when(publishedVideoSource.findById(10001L)).thenReturn(Optional.of(document));

        consumer.onMessage(event("video_stats", "UPDATE", Map.of(
                "video_id", "10001",
                "view_count", "1200",
                "like_count", "88")));

        assertThat(document.getViewCount()).isEqualTo(1200L);
        assertThat(document.getLikeCount()).isEqualTo(88L);
        verify(videoDocumentRepository).save(document);
        verify(stringRedisTemplate).delete("video:detail:10001");
    }

    @Test
    void delayedDeleteEventCannotRemoveRepublishedVideo() {
        VideoDocument current = new VideoDocument();
        current.setId(10001L);
        current.setTitle("重新发布的视频");
        when(publishedVideoSource.findById(10001L)).thenReturn(Optional.of(current));

        consumer.onMessage(event("video", "DELETE", Map.of("id", "10001")));

        verify(videoDocumentRepository).save(current);
        verify(videoDocumentRepository, never()).deleteById(10001L);
    }

    @Test
    void shouldRefreshFollowingCacheFromCanalState() {
        consumer.onMessage(event("follow", "UPDATE", Map.of(
                "follower_id", "1",
                "followed_id", "2",
                "status", "1")));

        verify(setOperations).add("follow:following:1", "2");
    }

    @Test
    void shouldRemoveCancelledLikeFromCache() {
        consumer.onMessage(event("user_like", "UPDATE", Map.of(
                "user_id", "1",
                "target_type", "1",
                "target_id", "10001",
                "status", "0")));

        verify(setOperations).remove("like:1:10001", "1");
    }

    private CacheSyncEvent event(String table, String eventType, Map<String, String> data) {
        CacheSyncEvent event = new CacheSyncEvent();
        event.setDatabase("bilibili");
        event.setTable(table);
        event.setEventType(eventType);
        event.setData(data);
        return event;
    }
}
