package com.root.aishopback.config;

import com.root.aishopback.websocket.MonitorWebSocketHandler;
import com.root.aishopback.websocket.MonitorWsAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private MonitorWebSocketHandler monitorWebSocketHandler;
    @Autowired
    private MonitorWsAuthInterceptor monitorWsAuthInterceptor;
    @Value("${app.monitor.ws-allowed-origins:http://localhost:8080,http://127.0.0.1:8080,http://localhost:8081,http://127.0.0.1:8081}")
    private String wsAllowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = wsAllowedOrigins.split("\\s*,\\s*");
        registry.addHandler(monitorWebSocketHandler, "/ws/monitor")
                .addInterceptors(monitorWsAuthInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }
}
