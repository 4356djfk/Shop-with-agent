package com.root.aishopback.netty.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class MonitorServerInitializer extends ChannelInitializer<SocketChannel> {
    private final String monitorSharedSecret;

    public MonitorServerInitializer(String monitorSharedSecret) {
        this.monitorSharedSecret = monitorSharedSecret;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // Heartbeat detection handler (e.g. 60 seconds without data implies disconnected)
        pipeline.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));

        // Solves TCP stickiness/fragmentation by splitting packets on newlines
        pipeline.addLast(new LineBasedFrameDecoder(4096));

        // String serialization and payload codec
        pipeline.addLast(new StringDecoder());
        pipeline.addLast(new StringEncoder());

        // Custom business logic handler
        pipeline.addLast(new MonitorServerHandler(monitorSharedSecret));
    }
}
