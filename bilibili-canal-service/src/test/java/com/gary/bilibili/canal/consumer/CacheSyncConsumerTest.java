package com.gary.bilibili.canal.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.canal.document.VideoDocument;
import com.gary.bilibili.canal.message.CacheSyncEvent;
import com.gary.bilibili.canal.repository.VideoDocumentRepository;
import com.gary.bilibili.canal.service.VideoBloomFilter;
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

class CacheSyncConsumerTest {

    private VideoDocumentRepository videoDocumentRepository;
    private StringRedisTemplate stringRedisTemplate;
    private SetOperations<String, String> setOperations;
    private VideoBloomFilter videoBloomFilter;
    private ReliableMessageExecutor reliableMessageExecutor;
    private CacheSyncConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        videoDocumentRepository = mock(VideoDocumentRepository.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        videoBloomFilter = mock(VideoBloomFilter.class);
        reliableMessageExecutor = mock(ReliableMessageExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(4).run();
            return null;
        }).when(reliableMessageExecutor).execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        consumer = new CacheSyncConsumer(
                videoDocumentRepository, stringRedisTemplate, videoBloomFilter,
                reliableMessageExecutor);
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
        when(videoDocumentRepository.findById(10001L)).thenReturn(Optional.empty());

        consumer.onMessage(event("video", "UPDATE", data));

        ArgumentCaptor<VideoDocument> captor = ArgumentCaptor.forClass(VideoDocument.class);
        verify(videoDocumentRepository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(document -> {
            assertThat(document.getId()).isEqualTo(10001L);
            assertThat(document.getTags()).containsExactly("SpringBoot", "Vue3");
            assertThat(document.getCreateTime()).isEqualTo("2026-08-03T10:00:00");
        });
        verify(stringRedisTemplate).delete("video:detail:10001");
        verify(videoBloomFilter).put(10001L);
    }

    @Test
    void shouldRemoveOfflineVideoFromIndex() {
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
        when(videoDocumentRepository.findById(10001L)).thenReturn(Optional.of(document));

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
