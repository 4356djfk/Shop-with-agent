package com.root.aishopback.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIMEOUT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Cache the latest status of each actual server to sync with new dashboard connections (refresh issue fix)
    private static final ConcurrentMap<String, Map<String, Object>> latestServerStatusCache = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
            session,
            SEND_TIMEOUT_MS,
            BUFFER_SIZE_LIMIT_BYTES
        );
        sessions.put(safeSession.getId(), safeSession);
        System.out.println("[WebSocket] Frontend dashboard connected. Session ID: " + safeSession.getId());
        
        // Push currently cached online servers to the new session
        for (Map<String, Object> statusObj : latestServerStatusCache.values()) {
            try {
                safeSession.sendMessage(new TextMessage(com.alibaba.fastjson2.JSON.toJSONString(statusObj)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("[WebSocket] Frontend dashboard disconnected. Session ID: " + session.getId());
    }

    /**
     * Broadcast message to all connected frontend dashboards
     */
    public static void broadcastMessage(String message, Map<String, Object> eventObj) {
        
        // Update cache so F5 Refreshes will get the latest state
        if (eventObj != null) {
            String serverId = (String) eventObj.get("serverId");
            String type = (String) eventObj.get("type");
            if ("OFFLINE".equals(type)) {
                latestServerStatusCache.remove(serverId);
            } else {
                Map<String, Object> cached = latestServerStatusCache.getOrDefault(serverId, new HashMap<>());
                cached.putAll(eventObj); // merge latest updates like cpu/memory into the cache
                latestServerStatusCache.put(serverId, cached);
            }
        }

        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    System.err.println("[WebSocket] Failed to send message to session: " + session.getId());
                    e.printStackTrace();
                    sessions.remove(session.getId());
                    try {
                        session.close();
                    } catch (IOException ignored) {
                        // ignore close error
                    }
                }
            }
        }
    }
}
