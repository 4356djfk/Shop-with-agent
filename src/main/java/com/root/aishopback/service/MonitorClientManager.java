package com.root.aishopback.service;

import com.root.aishopback.netty.client.NettyMonitorClient;
import com.alibaba.fastjson2.JSON;
import com.root.aishopback.websocket.MonitorWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitorClientManager {

    // Maps username to their dedicated monitor client thread runner
    private final Map<String, NettyMonitorClient> clientMap = new ConcurrentHashMap<>();
    private final String monitorSharedSecret;

    public MonitorClientManager(@Value("${app.monitor.shared-secret:change-me-dev-secret}") String monitorSharedSecret) {
        this.monitorSharedSecret = monitorSharedSecret;
    }

    /**
     * Spawns a new Netty monitor client for the given account if one doesn't exist.
     */
    public void startClientForUser(String account) {
        if (clientMap.containsKey(account)) {
            // Already tracking this user
            return;
        }
        
        NettyMonitorClient client = new NettyMonitorClient(account, monitorSharedSecret);
        clientMap.put(account, client);
        
        Thread clientThread = new Thread(client);
        clientThread.setDaemon(true);
        clientThread.start();
        System.out.println("[ClientManager] Spawning new background MonitorClient thread for user: " + account);
    }

    /**
     * Stops the Netty monitor client for the given account.
     */
    public void stopClientForUser(String account) {
        NettyMonitorClient client = clientMap.remove(account);
        if (client != null) {
            client.stop();
            System.out.println("[ClientManager] Stopped background MonitorClient thread for user: " + account);
            
            // Immediately broadcast OFFLINE to websocket so frontend updates without waiting for Netty channel timeout
            Map<String, Object> event = new HashMap<>();
            event.put("type", "OFFLINE");
            event.put("serverId", account);
            event.put("serverName", account);
            event.put("timestamp", System.currentTimeMillis());
            MonitorWebSocketHandler.broadcastMessage(JSON.toJSONString(event), event);
        }
    }
}
