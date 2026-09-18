package com.gary.bilibili.canal.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.canal.constant.CanalConstant;
import com.gary.bilibili.canal.message.CacheSyncEvent;
import com.gary.bilibili.canal.repository.VideoDocumentRepository;
import com.gary.bilibili.canal.service.VideoBloomFilter;
import com.gary.bilibili.canal.service.VideoIndexWriteGate;
import com.gary.bilibili.canal.service.MySqlNamedLock;
import com.gary.bilibili.canal.service.PublishedVideoSource;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@RocketMQMessageListener(
        topic = CanalConstant.CACHE_SYNC_TOPIC,
        consumerGroup = CanalConstant.CACHE_SYNC_CONSUMER_GROUP)
public class CacheSyncConsumer implements RocketMQListener<CacheSyncEvent> {

    private static final String CONSUMER_GROUP = CanalConstant.CACHE_SYNC_CONSUMER_GROUP;
    private static final int CDC_LOCK_TIMEOUT_SECONDS = 30;

    private final VideoDocumentRepository videoDocumentRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoBloomFilter videoBloomFilter;
    private final ReliableMessageExecutor reliableMessageExecutor;
    private final VideoIndexWriteGate videoIndexWriteGate;
    private final MySqlNamedLock distributedLock;
    private final PublishedVideoSource publishedVideoSource;

    public CacheSyncConsumer(VideoDocumentRepository videoDocumentRepository,
                             StringRedisTemplate stringRedisTemplate,
                             VideoBloomFilter videoBloomFilter,
                             ReliableMessageExecutor reliableMessageExecutor,
                             VideoIndexWriteGate videoIndexWriteGate,
                             MySqlNamedLock distributedLock,
                             PublishedVideoSource publishedVideoSource) {
        this.videoDocumentRepository = videoDocumentRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.videoBloomFilter = videoBloomFilter;
        this.reliableMessageExecutor = reliableMessageExecutor;
        this.videoIndexWriteGate = videoIndexWriteGate;
        this.distributedLock = distributedLock;
        this.publishedVideoSource = publishedVideoSource;
    }

    @Override
    public void onMessage(CacheSyncEvent event) {
        if (event == null || !StringUtils.hasText(event.getTable()) || event.getData() == null) {
            return;
        }
        reliableMessageExecutor.execute(
                CanalConstant.CACHE_SYNC_TOPIC,
                CONSUMER_GROUP,
                messageKey(event),
                event,
                () -> {
                    if (CanalConstant.VIDEO_TABLE.equals(event.getTable())
                            || CanalConstant.VIDEO_STATS_TABLE.equals(event.getTable())) {
                        distributedLock.execute(VideoIndexWriteGate.DISTRIBUTED_LOCK_NAME,
                                CDC_LOCK_TIMEOUT_SECONDS, () -> {
                                    videoIndexWriteGate.withCdcWrite(() -> sync(event));
                                    return null;
                                });
                    } else {
                        sync(event);
                    }
                });
    }

    private void sync(CacheSyncEvent event) {
        switch (event.getTable()) {
            case CanalConstant.VIDEO_TABLE -> syncVideo(event.getData(), "id");
            case CanalConstant.VIDEO_STATS_TABLE -> syncVideoStats(event.getData());
            case CanalConstant.USER_TABLE -> evictUserCache(event.getData());
            case CanalConstant.FOLLOW_TABLE -> syncFollowCache(event);
            case CanalConstant.LIKE_TABLE -> syncLikeCache(event);
            default -> {
            }
        }
    }

    private String messageKey(CacheSyncEvent event) {
        if (StringUtils.hasText(event.getEventId())) {
            return event.getEventId();
        }
        Object id = event.getData().getOrDefault("id",
                event.getData().getOrDefault("video_id", "unknown"));
        return event.getTable() + ":" + event.getEventType() + ":" + id;
    }

    private void syncVideo(Map<String, String> data, String idColumn) {
        Long videoId = getLong(data, idColumn);
        if (videoId == null) {
            return;
        }
        evictVideoCache(videoId);
        // Events for the same video may arrive out of order across MQ consumers.
        // Read the committed MySQL state while holding the shared index lock.
        var current = publishedVideoSource.findById(videoId);
        if (current.isEmpty()) {
            videoDocumentRepository.deleteById(videoId);
            return;
        }
        videoDocumentRepository.save(current.get());
        videoBloomFilter.put(videoId);
    }

    private void syncVideoStats(Map<String, String> data) {
        syncVideo(data, "video_id");
    }

    private void evictUserCache(Map<String, String> data) {
        Long userId = getLong(data, "id");
        if (userId != null) {
            stringRedisTemplate.delete(CanalConstant.USER_INFO_CACHE_KEY_PREFIX + userId);
        }
    }

    private void syncFollowCache(CacheSyncEvent event) {
        Map<String, String> data = event.getData();
        Long followerId = getLong(data, "follower_id");
        Long followedId = getLong(data, "followed_id");
        if (followerId == null || followedId == null) {
            return;
        }
        boolean active = !CanalConstant.EVENT_DELETE.equals(event.getEventType())
                && getInt(data, "status", 0) == 1;
        String key = CanalConstant.FOLLOWING_CACHE_KEY_PREFIX + followerId;
        if (active) {
            stringRedisTemplate.opsForSet().add(key, followedId.toString());
        } else {
            stringRedisTemplate.opsForSet().remove(key, followedId.toString());
        }
    }

    private void syncLikeCache(CacheSyncEvent event) {
        Map<String, String> data = event.getData();
        Long userId = getLong(data, "user_id");
        Integer targetType = getInteger(data, "target_type");
        Long targetId = getLong(data, "target_id");
        if (userId == null || targetType == null || targetId == null) {
            return;
        }
        boolean active = !CanalConstant.EVENT_DELETE.equals(event.getEventType())
                && getInt(data, "status", 0) == 1;
        String key = CanalConstant.LIKE_CACHE_KEY_PREFIX + targetType + ":" + targetId;
        if (active) {
            stringRedisTemplate.opsForSet().add(key, userId.toString());
        } else {
            stringRedisTemplate.opsForSet().remove(key, userId.toString());
        }
    }

    private void evictVideoCache(Long videoId) {
        stringRedisTemplate.delete(CanalConstant.VIDEO_DETAIL_CACHE_KEY_PREFIX + videoId);
    }

    private Long getLong(Map<String, String> data, String key) {
        String value = data.get(key);
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }


    private Integer getInteger(Map<String, String> data, String key) {
        String value = data.get(key);
        return StringUtils.hasText(value) ? Integer.valueOf(value) : null;
    }

    private int getInt(Map<String, String> data, String key, int defaultValue) {
        Integer value = getInteger(data, key);
        return value == null ? defaultValue : value;
    }

}
