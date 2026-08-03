package com.gary.bilibili.danmu.netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.danmu.vo.DanmuBroadcastVO;
import com.gary.bilibili.danmu.vo.WebSocketMessageVO;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class DanmuRoomManager {

    private static final Logger log = LoggerFactory.getLogger(DanmuRoomManager.class);

    private final ConcurrentHashMap<Long, ChannelGroup> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public DanmuRoomManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void join(Long videoId, Channel channel) {
        rooms.computeIfAbsent(videoId,
                key -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)).add(channel);
    }

    public void leave(Long videoId, Channel channel) {
        if (videoId == null) {
            return;
        }
        ChannelGroup room = rooms.get(videoId);
        if (room == null) {
            return;
        }
        room.remove(channel);
        if (room.isEmpty()) {
            rooms.remove(videoId, room);
        }
    }

    public void broadcast(DanmuBroadcastVO danmu) {
        ChannelGroup room = rooms.get(danmu.getVideoId());
        if (room == null || room.isEmpty()) {
            return;
        }
        WebSocketMessageVO message = new WebSocketMessageVO();
        message.setType("danmu");
        message.setData(danmu);
        try {
            room.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(message)));
        } catch (JsonProcessingException exception) {
            log.error("Serialize danmu broadcast failed, danmuId={}", danmu.getId(), exception);
        }
    }
}
