package com.root.aishopback.netty.server;

import com.alibaba.fastjson2.JSON;
import com.root.aishopback.netty.message.MonitorMessage;
import com.root.aishopback.security.HmacSignatureUtil;
import com.root.aishopback.websocket.MonitorWebSocketHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.HashMap;
import java.util.Map;

public class MonitorServerHandler extends SimpleChannelInboundHandler<String> {

    // Store account associated with the channel to send OFFLINE event upon disconnect
    private String currentAccount = null;
    private final String monitorSharedSecret;

    public MonitorServerHandler(String monitorSharedSecret) {
        this.monitorSharedSecret = monitorSharedSecret;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        if (currentAccount != null) {
            // Notify frontend that this server went offline
            Map<String, Object> event = new HashMap<>();
            event.put("type", "OFFLINE");
            event.put("serverId", currentAccount);
            event.put("serverName", currentAccount);
            event.put("timestamp", System.currentTimeMillis());
            MonitorWebSocketHandler.broadcastMessage(JSON.toJSONString(event), event);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        MonitorMessage monitorMsg;
        try {
            monitorMsg = JSON.parseObject(msg, MonitorMessage.class);
        } catch (Exception e) {
            System.err.println("Data format parsing error: " + msg);
            e.printStackTrace();
            return;
        }

        if (monitorMsg == null) {
            return;
        }

        try {
            if (!isValidSignedMessage(monitorMsg)) {
                System.err.println("[MonitorServer] Rejecting unsigned/invalid monitor message.");
                ctx.close();
                return;
            }

            if (currentAccount == null && monitorMsg.getAccount() != null) {
                currentAccount = monitorMsg.getAccount();
                // On first message, send REGISTER event to dashboard
                Map<String, Object> registerEvent = new HashMap<>();
                registerEvent.put("type", "REGISTER");
                registerEvent.put("serverId", monitorMsg.getAccount());
                registerEvent.put("serverName", monitorMsg.getAccount());
                registerEvent.put("serverIp", monitorMsg.getIp());
                registerEvent.put("timestamp", System.currentTimeMillis());
                MonitorWebSocketHandler.broadcastMessage(JSON.toJSONString(registerEvent), registerEvent);
            }

            if ("HEARTBEAT".equals(monitorMsg.getType())) {
                System.out.println("[Server] Received HEARTBEAT from account " + monitorMsg.getAccount());
                // Broadcast partial heartbeat to frontend
                Map<String, Object> event = new HashMap<>();
                event.put("type", "HEARTBEAT");
                event.put("serverId", monitorMsg.getAccount());
                event.put("timestamp", System.currentTimeMillis());
                MonitorWebSocketHandler.broadcastMessage(JSON.toJSONString(event), event);

            } else if ("DATA".equals(monitorMsg.getType())) {
                // Broadcast data metrics to frontend
                Map<String, Object> event = new HashMap<>();
                event.put("type", "HEARTBEAT"); // Frontend uses HEARTBEAT to update stats
                event.put("serverId", monitorMsg.getAccount());
                event.put("timestamp", System.currentTimeMillis());
                event.put("cpu", monitorMsg.getCpuUsage());
                event.put("memory", monitorMsg.getMemoryUsage());
                event.put("disk", monitorMsg.getDiskUsage());
                event.put("latency", monitorMsg.getNetworkLatency());
                event.put("ip", monitorMsg.getIp());
                MonitorWebSocketHandler.broadcastMessage(JSON.toJSONString(event), event);
            }
        } catch (Exception e) {
            System.err.println("[MonitorServer] Message handling error: " + msg);
            e.printStackTrace();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                // Read idle event implies client timeout (i.e stopped sending heartbeat/data within 60s)
                System.out.println("Client read timeout, closing connection: " + ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private boolean isValidSignedMessage(MonitorMessage monitorMsg) {
        if (monitorMsg.getAccount() == null || monitorMsg.getAccount().isBlank()) return false;
        if (monitorMsg.getType() == null || monitorMsg.getType().isBlank()) return false;
        if (monitorMsg.getSignature() == null || monitorMsg.getSignature().isBlank()) return false;
        if (monitorMsg.getTimestamp() <= 0) return false;

        long now = System.currentTimeMillis();
        if (Math.abs(now - monitorMsg.getTimestamp()) > 60_000) {
            return false;
        }

        String payload = monitorMsg.getAccount() + "|" + monitorMsg.getType() + "|" + monitorMsg.getTimestamp();
        String expected = HmacSignatureUtil.sign(payload, monitorSharedSecret);
        return HmacSignatureUtil.safeEquals(expected, monitorMsg.getSignature());
    }
}
