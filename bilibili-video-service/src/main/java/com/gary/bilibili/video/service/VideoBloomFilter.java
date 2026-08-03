package com.gary.bilibili.video.service;

import com.gary.bilibili.video.constant.VideoConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class VideoBloomFilter {

    private static final Logger log = LoggerFactory.getLogger(VideoBloomFilter.class);
    private static final long BIT_SIZE = 1L << 24;
    private static final int HASH_COUNT = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private volatile boolean ready;

    public VideoBloomFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean put(Long videoId) {
        if (videoId == null) {
            return false;
        }
        try {
            for (int i = 0; i < HASH_COUNT; i++) {
                stringRedisTemplate.opsForValue().setBit(
                        VideoConstant.BLOOM_FILTER_KEY, hash(videoId, i), true);
            }
            return true;
        } catch (Exception exception) {
            log.warn("Add video id to bloom filter failed, videoId={}", videoId, exception);
            return false;
        }
    }

    public boolean mightContain(Long videoId) {
        if (!ready || videoId == null) {
            return true;
        }
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(VideoConstant.BLOOM_FILTER_KEY))) {
                ready = false;
                return true;
            }
            for (int i = 0; i < HASH_COUNT; i++) {
                Boolean exists = stringRedisTemplate.opsForValue().getBit(
                        VideoConstant.BLOOM_FILTER_KEY, hash(videoId, i));
                if (!Boolean.TRUE.equals(exists)) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            log.warn("Check video bloom filter failed, videoId={}", videoId, exception);
            return true;
        }
    }

    public void markReady() {
        this.ready = true;
    }

    private long hash(Long videoId, int seed) {
        long value = videoId ^ (0x9E3779B97F4A7C15L * (seed + 1));
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value = value ^ (value >>> 31);
        return Math.floorMod(value, BIT_SIZE);
    }
}
