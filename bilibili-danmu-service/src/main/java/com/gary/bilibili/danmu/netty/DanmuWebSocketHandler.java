package com.gary.bilibili.danmu.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.danmu.mapper.DanmuMapper;
import com.gary.bilibili.danmu.service.DanmuWebSocketTicketService;
import com.gary.bilibili.danmu.vo.DanmuAuthVO;
import com.gary.bilibili.danmu.vo.WebSocketMessageVO;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ChannelHandler.Sharable
public class DanmuWebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(DanmuWebSocketHandler.class);
    private static final Pattern PATH_PATTERN = Pattern.compile("^/api/danmu/ws/(\\d+)$");
    private static final AttributeKey<Long> VIDEO_ID_KEY = AttributeKey.valueOf("danmuVideoId");
    private static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("danmuUserId");
    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER_KEY =
            AttributeKey.valueOf("danmuHandshaker");

    private final DanmuMapper danmuMapper;
    private final DanmuRoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final DanmuWebSocketTicketService webSocketTicketService;

    public DanmuWebSocketHandler(DanmuMapper danmuMapper,
                                 DanmuRoomManager roomManager,
                                 ObjectMapper objectMapper,
                                 DanmuWebSocketTicketService webSocketTicketService) {
        this.danmuMapper = danmuMapper;
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
        this.webSocketTicketService = webSocketTicketService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object message) throws Exception {
        if (message instanceof FullHttpRequest request) {
            handleHandshake(context, request);
            return;
        }
        if (message instanceof WebSocketFrame frame) {
            handleFrame(context, frame);
        }
    }

    private void handleHandshake(ChannelHandlerContext context, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess() || request.method() != HttpMethod.GET) {
            sendHttpError(context, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Matcher matcher = PATH_PATTERN.matcher(decoder.path());
        if (!matcher.matches()) {
            sendHttpError(context, HttpResponseStatus.NOT_FOUND);
            return;
        }
        Long videoId = Long.valueOf(matcher.group(1));
        String ticket = first(decoder.parameters().get("ticket"));
        Long userId = webSocketTicketService.consume(ticket, videoId);
        if (ticket != null && userId == null) {
            sendHttpError(context, HttpResponseStatus.UNAUTHORIZED);
            return;
        }
        String location = "ws://" + request.headers().get(HttpHeaderNames.HOST) + request.uri();
        WebSocketServerHandshakerFactory factory =
                new WebSocketServerHandshakerFactory(location, null, true, 65536);
        WebSocketServerHandshaker handshaker = factory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
            return;
        }

        context.channel().attr(HANDSHAKER_KEY).set(handshaker);
        handshaker.handshake(context.channel(), request).addListener(future -> {
            if (!future.isSuccess()) {
                context.close();
                return;
            }
            if (!isPublishedVideo(videoId)) {
                sendWebSocketError(context, "视频不存在", true);
                return;
            }
            context.channel().attr(VIDEO_ID_KEY).set(videoId);
            context.channel().attr(USER_ID_KEY).set(userId);
            roomManager.join(videoId, context.channel());
            sendAuthMessage(context, videoId, userId);
        });
    }

    private void handleFrame(ChannelHandlerContext context, WebSocketFrame frame) throws Exception {
        if (frame instanceof CloseWebSocketFrame closeFrame) {
            WebSocketServerHandshaker handshaker = context.channel().attr(HANDSHAKER_KEY).get();
            if (handshaker != null) {
                handshaker.close(context.channel(), closeFrame.retain());
            } else {
                context.close();
            }
            return;
        }
        if (frame instanceof PingWebSocketFrame pingFrame) {
            context.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            return;
        }
        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            sendWebSocketError(context, "不支持的消息类型", false);
            return;
        }

        JsonNode node = objectMapper.readTree(textFrame.text());
        if (node.has("type") && "heartbeat".equals(node.get("type").asText())) {
            WebSocketMessageVO heartbeat = new WebSocketMessageVO();
            heartbeat.setType("heartbeat");
            heartbeat.setTimestamp(System.currentTimeMillis());
            sendMessage(context, heartbeat);
            return;
        }
        if (context.channel().attr(USER_ID_KEY).get() == null) {
            sendWebSocketError(context, "请先登录", false);
            return;
        }
        sendWebSocketError(context, "请通过 HTTP 接口发送弹幕", false);
    }

    private void sendAuthMessage(ChannelHandlerContext context, Long videoId, Long userId) {
        DanmuAuthVO auth = new DanmuAuthVO();
        auth.setVideoId(videoId);
        auth.setUserId(userId);

        WebSocketMessageVO message = new WebSocketMessageVO();
        message.setType("auth");
        message.setSuccess(true);
        message.setData(auth);
        sendMessage(context, message);
    }

    private void sendWebSocketError(ChannelHandlerContext context, String error, boolean close) {
        WebSocketMessageVO message = new WebSocketMessageVO();
        message.setType("error");
        message.setMessage(error);
        try {
            TextWebSocketFrame frame = new TextWebSocketFrame(objectMapper.writeValueAsString(message));
            if (close) {
                context.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
            } else {
                context.writeAndFlush(frame);
            }
        } catch (Exception exception) {
            context.close();
        }
    }

    private void sendMessage(ChannelHandlerContext context, WebSocketMessageVO message) {
        try {
            context.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(message)));
        } catch (Exception exception) {
            log.warn("Serialize websocket message failed", exception);
            context.close();
        }
    }

    private String first(List<String> values) {
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            return null;
        }
        return values.getFirst();
    }

    private boolean isPublishedVideo(Long videoId) {
        Long count = danmuMapper.countPublishedVideo(videoId);
        return count != null && count > 0;
    }

    private void sendHttpError(ChannelHandlerContext context, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(status.toString(), CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        roomManager.leave(context.channel().attr(VIDEO_ID_KEY).get(), context.channel());
        super.channelInactive(context);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            context.close();
            return;
        }
        super.userEventTriggered(context, event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        log.warn("Danmu websocket connection exception, channel={}", context.channel().id(), cause);
        context.close();
    }
}
