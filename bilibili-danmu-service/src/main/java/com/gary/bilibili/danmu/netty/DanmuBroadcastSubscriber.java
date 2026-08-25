package com.gary.bilibili.danmu.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.danmu.vo.DanmuBroadcastVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class DanmuBroadcastSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DanmuBroadcastSubscriber.class);

    private final ObjectMapper objectMapper;
    private final DanmuRoomManager roomManager;

    public DanmuBroadcastSubscriber(ObjectMapper objectMapper,
                                    DanmuRoomManager roomManager) {
        this.objectMapper = objectMapper;
        this.roomManager = roomManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DanmuBroadcastVO danmu = objectMapper.readValue(
                    message.getBody(), DanmuBroadcastVO.class);
            roomManager.broadcastLocal(danmu);
        } catch (Exception exception) {
            log.warn("Consume danmu broadcast event failed", exception);
        }
    }
}
