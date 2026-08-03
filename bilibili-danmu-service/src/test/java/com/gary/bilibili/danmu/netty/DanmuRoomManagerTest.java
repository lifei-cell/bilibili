package com.gary.bilibili.danmu.netty;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.gary.bilibili.danmu.vo.DanmuBroadcastVO;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DanmuRoomManagerTest {

    @Test
    void shouldBroadcastOnlyToChannelsInSameVideoRoom() {
        DanmuRoomManager roomManager = new DanmuRoomManager(
                JsonMapper.builder().findAndAddModules().build());
        EmbeddedChannel currentVideoChannel = new EmbeddedChannel();
        EmbeddedChannel otherVideoChannel = new EmbeddedChannel();
        roomManager.join(10001L, currentVideoChannel);
        roomManager.join(10002L, otherVideoChannel);

        DanmuBroadcastVO danmu = new DanmuBroadcastVO();
        danmu.setId(90001L);
        danmu.setVideoId(10001L);
        danmu.setContent("这个地方讲得很清楚");
        roomManager.broadcast(danmu);

        TextWebSocketFrame frame = currentVideoChannel.readOutbound();
        Object otherFrame = otherVideoChannel.readOutbound();
        assertThat(frame.text()).contains("\"type\":\"danmu\"");
        assertThat(otherFrame).isNull();
        frame.release();
        currentVideoChannel.finishAndReleaseAll();
        otherVideoChannel.finishAndReleaseAll();
    }
}
