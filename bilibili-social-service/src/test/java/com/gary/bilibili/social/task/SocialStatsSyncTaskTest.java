package com.gary.bilibili.social.task;

import com.gary.bilibili.social.mapper.SocialStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialStatsSyncTaskTest {

    private SocialStatsMapper socialStatsMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private HashOperations<String, Object, Object> hashOperations;
    private SocialStatsSyncTask syncTask;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        socialStatsMapper = mock(SocialStatsMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);
        hashOperations = mock(HashOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        syncTask = new SocialStatsSyncTask(socialStatsMapper, stringRedisTemplate);
    }

    @Test
    void shouldSyncPositiveAndNegativeSocialDeltas() {
        when(setOperations.members("video:stats:social:pending"))
                .thenReturn(Set.of("10001"));
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.eq("video:stats:social:sync:lock:10001"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(hashOperations.get("video:stats:10001", "likeCount")).thenReturn("2");
        when(hashOperations.get("video:stats:10001", "collectCount")).thenReturn("-1");
        when(hashOperations.get("video:stats:10001", "commentCount")).thenReturn(null);
        when(socialStatsMapper.incrementLikeCount(10001L, 2L)).thenReturn(1);
        when(socialStatsMapper.incrementCollectCount(10001L, -1L)).thenReturn(1);

        syncTask.syncSocialStats();

        verify(socialStatsMapper).incrementLikeCount(10001L, 2L);
        verify(socialStatsMapper).incrementCollectCount(10001L, -1L);
        verify(stringRedisTemplate).delete("video:detail:10001");
    }
}
