package com.root.aishopback.netty.client;

import com.alibaba.fastjson2.JSON;
import com.root.aishopback.netty.message.MonitorMessage;
import com.root.aishopback.security.HmacSignatureUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class MonitorClientHandler extends SimpleChannelInboundHandler<String> {

    private final String account;
    private final String sharedSecret;
    private final OshiMonitor oshiMonitor;

    // Heartbeat counter to distinguish between pure heartbeat and data
    private int sendCount = 0;

    public MonitorClientHandler(String account, String sharedSecret) {
        this.account = account;
        this.sharedSecret = sharedSecret;
        this.oshiMonitor = new OshiMonitor();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Successfully connected to the Monitor Server!");
        sendHeartbeatAndData(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        // Handle server reply here if needed
        System.out.println("[Client] Server reply: " + msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // Writer idle indicates it's time to send data
                sendHeartbeatAndData(ctx);
            }
        }
    }

    private void sendHeartbeatAndData(ChannelHandlerContext ctx) {
        sendCount++;
        
        // Example logic:
        // Every interval, send actual DATA (which also counts as keeping connection alive).
        // Or send HEARTBEAT only if no real data is available. Since we need real-time, we just send DATA every tick.
        
        MonitorMessage message = new MonitorMessage();
        message.setAccount(this.account);
        
        // Depending on your requirements, if you only want heartbeat, you can separate the message:
        if (sendCount % 2 == 0) {
           message.setType("HEARTBEAT");
           message.setCpuUsage(0);
           message.setMemoryUsage(0);
           message.setDiskUsage(0);
           message.setNetworkLatency(0);
        } else {
           message.setType("DATA");
           message.setCpuUsage(oshiMonitor.getCpuUsage());
           message.setMemoryUsage(oshiMonitor.getMemoryUsage());
           message.setDiskUsage(oshiMonitor.getDiskUsage());
           message.setNetworkLatency(oshiMonitor.getNetworkLatency());
           message.setIp(oshiMonitor.getIpAddress());
        }
        long ts = System.currentTimeMillis();
        message.setTimestamp(ts);
        String payload = message.getAccount() + "|" + message.getType() + "|" + ts;
        message.setSignature(HmacSignatureUtil.sign(payload, sharedSecret));

        String json = JSON.toJSONString(message);
        ctx.writeAndFlush(json + "\n");
        System.out.println("[Client] Sent -> " + message.getType());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
