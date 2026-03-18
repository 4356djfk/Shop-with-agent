package com.root.aishopback.netty.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class MonitorClientInitializer extends ChannelInitializer<SocketChannel> {

    private final String account;
    private final String sharedSecret;

    public MonitorClientInitializer(String account, String sharedSecret) {
        this.account = account;
        this.sharedSecret = sharedSecret;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // Send heartbeat/data triggers every 5 seconds idle event on writing.
        pipeline.addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS));

        // Solves TCP stickiness/fragmentation by splitting packets on newlines
        pipeline.addLast(new LineBasedFrameDecoder(4096));

        // String serialization and payload codec
        pipeline.addLast(new StringDecoder());
        pipeline.addLast(new StringEncoder());

        // Custom client business logic handler
        pipeline.addLast(new MonitorClientHandler(account, sharedSecret));
    }
}
