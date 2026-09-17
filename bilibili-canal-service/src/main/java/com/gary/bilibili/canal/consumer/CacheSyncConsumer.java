package com.gary.bilibili.canal.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.canal.constant.CanalConstant;
import com.gary.bilibili.canal.document.VideoDocument;
import com.gary.bilibili.canal.message.CacheSyncEvent;
import com.gary.bilibili.canal.repository.VideoDocumentRepository;
import com.gary.bilibili.canal.service.VideoBloomFilter;
import com.gary.bilibili.canal.service.VideoIndexWriteGate;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RocketMQMessageListener(
        topic = CanalConstant.CACHE_SYNC_TOPIC,
        consumerGroup = CanalConstant.CACHE_SYNC_CONSUMER_GROUP)
public class CacheSyncConsumer implements RocketMQListener<CacheSyncEvent> {

    private static final String CONSUMER_GROUP = CanalConstant.CACHE_SYNC_CONSUMER_GROUP;

    private final VideoDocumentRepository videoDocumentRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoBloomFilter videoBloomFilter;
    private final ReliableMessageExecutor reliableMessageExecutor;
    private final VideoIndexWriteGate videoIndexWriteGate;

    public CacheSyncConsumer(VideoDocumentRepository videoDocumentRepository,
                             StringRedisTemplate stringRedisTemplate,
                             VideoBloomFilter videoBloomFilter,
                             ReliableMessageExecutor reliableMessageExecutor,
                             VideoIndexWriteGate videoIndexWriteGate) {
        this.videoDocumentRepository = videoDocumentRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.videoBloomFilter = videoBloomFilter;
        this.reliableMessageExecutor = reliableMessageExecutor;
        this.videoIndexWriteGate = videoIndexWriteGate;
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
                        videoIndexWriteGate.withCdcWrite(() -> sync(event));
                    } else {
                        sync(event);
                    }
                });
    }

    private void sync(CacheSyncEvent event) {
        switch (event.getTable()) {
            case CanalConstant.VIDEO_TABLE -> syncVideo(event);
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

    private void syncVideo(CacheSyncEvent event) {
        Map<String, String> data = event.getData();
        Long videoId = getLong(data, "id");
        if (videoId == null) {
            return;
        }
        evictVideoCache(videoId);
        int status = getInt(data, "status", 0);
        int deleted = getInt(data, "deleted", 0);
        if (CanalConstant.EVENT_DELETE.equals(event.getEventType())
                || status != CanalConstant.VIDEO_STATUS_PUBLISHED
                || deleted == 1) {
            videoDocumentRepository.deleteById(videoId);
            return;
        }

        VideoDocument document = videoDocumentRepository.findById(videoId)
                .orElseGet(VideoDocument::new);
        document.setId(videoId);
        document.setTitle(data.get("title"));
        document.setDescription(data.get("description"));
        document.setTags(splitTags(data.get("tags")));
        document.setCategoryId(getLong(data, "category_id"));
        document.setUserId(getLong(data, "user_id"));
        document.setStatus(status);
        document.setViewCount(nullToZero(document.getViewCount()));
        document.setLikeCount(nullToZero(document.getLikeCount()));
        document.setCreateTime(normalizeDateTime(data.get("create_time")));
        videoDocumentRepository.save(document);
        videoBloomFilter.put(videoId);
    }

    private void syncVideoStats(Map<String, String> data) {
        Long videoId = getLong(data, "video_id");
        if (videoId == null) {
            return;
        }
        evictVideoCache(videoId);
        videoDocumentRepository.findById(videoId).ifPresent(document -> {
            document.setViewCount(getLong(data, "view_count", 0L));
            document.setLikeCount(getLong(data, "like_count", 0L));
            videoDocumentRepository.save(document);
        });
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

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalizeDateTime(String value) {
        return StringUtils.hasText(value) ? value.trim().replace(' ', 'T') : null;
    }

    private Long getLong(Map<String, String> data, String key) {
        String value = data.get(key);
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }

    private Long getLong(Map<String, String> data, String key, Long defaultValue) {
        Long value = getLong(data, key);
        return value == null ? defaultValue : value;
    }

    private Integer getInteger(Map<String, String> data, String key) {
        String value = data.get(key);
        return StringUtils.hasText(value) ? Integer.valueOf(value) : null;
    }

    private int getInt(Map<String, String> data, String key, int defaultValue) {
        Integer value = getInteger(data, key);
        return value == null ? defaultValue : value;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
