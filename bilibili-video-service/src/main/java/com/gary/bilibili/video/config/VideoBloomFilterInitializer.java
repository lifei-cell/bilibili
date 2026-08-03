package com.gary.bilibili.video.config;

import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.service.VideoBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VideoBloomFilterInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VideoBloomFilterInitializer.class);

    private final VideoMapper videoMapper;
    private final VideoBloomFilter videoBloomFilter;

    public VideoBloomFilterInitializer(VideoMapper videoMapper, VideoBloomFilter videoBloomFilter) {
        this.videoMapper = videoMapper;
        this.videoBloomFilter = videoBloomFilter;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Long> videoIds = videoMapper.selectPublishedIds();
            boolean initialized = true;
            for (Long videoId : videoIds) {
                if (!videoBloomFilter.put(videoId)) {
                    initialized = false;
                }
            }
            if (!initialized) {
                log.warn("Video bloom filter initialization is incomplete, queries will fall back to database");
                return;
            }
            videoBloomFilter.markReady();
            log.info("Video bloom filter initialized, videoCount={}", videoIds.size());
        } catch (Exception exception) {
            log.warn("Initialize video bloom filter failed, queries will fall back to database", exception);
        }
    }
}
