package com.gary.bilibili.canal.service;

import com.gary.bilibili.canal.constant.CanalConstant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class VideoBloomFilter {

    private static final long BIT_SIZE = 1L << 24;
    private static final int HASH_COUNT = 5;

    private final StringRedisTemplate stringRedisTemplate;

    public VideoBloomFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void put(Long videoId) {
        if (videoId == null) {
            return;
        }
        for (int i = 0; i < HASH_COUNT; i++) {
            stringRedisTemplate.opsForValue().setBit(
                    CanalConstant.VIDEO_BLOOM_FILTER_KEY, hash(videoId, i), true);
        }
    }

    private long hash(Long videoId, int seed) {
        long value = videoId ^ (0x9E3779B97F4A7C15L * (seed + 1));
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value = value ^ (value >>> 31);
        return Math.floorMod(value, BIT_SIZE);
    }
}
