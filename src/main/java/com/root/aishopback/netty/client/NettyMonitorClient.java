package com.root.aishopback.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NettyMonitorClient implements Runnable {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9000;
    
    // The account identifier simulated for this client terminal
    private final String account;
    private final String sharedSecret;

    private EventLoopGroup group;
    private ChannelFuture channelFuture;

    public NettyMonitorClient(String account, String sharedSecret) {
        this.account = account;
        this.sharedSecret = sharedSecret;
    }

    @Override
    public void run() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new MonitorClientInitializer(account, sharedSecret));

            // Start the client connection.
            channelFuture = b.connect(HOST, PORT).sync();
            System.out.println("Netty Monitor Client started, connecting to " + HOST + ":" + PORT + " using account: " + account);

            // Wait until the connection is closed.
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            System.out.println("Monitor client interrupted for account: " + account);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (group != null) {
                group.shutdownGracefully();
            }
            System.out.println("Monitor client stopped for account: " + account);
        }
    }

    public void stop() {
        if (channelFuture != null && channelFuture.channel() != null) {
            channelFuture.channel().close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        // Simulating the user 'admin123' running the monitor locally
        new Thread(new NettyMonitorClient("admin123", "change-me-dev-secret")).start();
    }
}
