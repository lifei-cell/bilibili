package com.gary.bilibili.danmu.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.danmu.constant.DanmuConstant;
import com.gary.bilibili.danmu.vo.DanmuBroadcastVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DanmuBroadcastPublisher {

    private static final Logger log = LoggerFactory.getLogger(DanmuBroadcastPublisher.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DanmuRoomManager roomManager;

    public DanmuBroadcastPublisher(StringRedisTemplate stringRedisTemplate,
                                   ObjectMapper objectMapper,
                                   DanmuRoomManager roomManager) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.roomManager = roomManager;
    }

    public void publish(DanmuBroadcastVO danmu) {
        try {
            Long receiverCount = stringRedisTemplate.convertAndSend(
                    DanmuConstant.BROADCAST_CHANNEL,
                    objectMapper.writeValueAsString(danmu));
            if (receiverCount == null || receiverCount == 0) {
                roomManager.broadcastLocal(danmu);
            }
        } catch (Exception exception) {
            log.warn("Publish danmu broadcast event failed, videoId={}; fallback to local room",
                    danmu.getVideoId(), exception);
            roomManager.broadcastLocal(danmu);
        }
    }
}
