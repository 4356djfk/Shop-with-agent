package com.root.aishopback.netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(prefix = "app.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NettyMonitorServer implements CommandLineRunner {

    private final int port;
    private final String monitorSharedSecret;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyMonitorServer(
        @Value("${app.monitor.port:9000}") int port,
        @Value("${app.monitor.shared-secret:change-me-dev-secret}") String monitorSharedSecret
    ) {
        this.port = port;
        this.monitorSharedSecret = monitorSharedSecret;
    }

    @Override
    public void run(String... args) throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new MonitorServerInitializer(monitorSharedSecret));

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Netty Monitor Server started on port " + port);
            
            // Do not call f.channel().closeFuture().sync() here because it will block the Spring Boot main thread.
        } catch (Exception e) {
            e.printStackTrace();
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("Netty Monitor Server stopped.");
    }
}
