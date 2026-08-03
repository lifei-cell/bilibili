package com.gary.bilibili.danmu.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class DanmuNettyServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DanmuNettyServer.class);

    private final DanmuWebSocketHandler webSocketHandler;
    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running;

    public DanmuNettyServer(DanmuWebSocketHandler webSocketHandler,
                            @Value("${danmu.netty.port:8093}") int port) {
        this.webSocketHandler = webSocketHandler;
        this.port = port;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        bossGroup = new MultiThreadIoEventLoopGroup(
                1, new DefaultThreadFactory("danmu-netty-boss"), NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(
                0, new DefaultThreadFactory("danmu-netty-worker"), NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline()
                                    .addLast(new IdleStateHandler(60, 0, 0))
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(65536))
                                    .addLast(webSocketHandler);
                        }
                    });
            serverChannel = bootstrap.bind(port).syncUninterruptibly().channel();
            running = true;
            log.info("Danmu Netty WebSocket server started, port={}", port);
        } catch (Exception exception) {
            shutdownGroups();
            throw new IllegalStateException("Start danmu Netty server failed", exception);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        shutdownGroups();
        running = false;
        log.info("Danmu Netty WebSocket server stopped");
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void shutdownGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}
