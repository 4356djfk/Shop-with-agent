package com.root.aishopback.websocket;

import com.root.aishopback.service.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class MonitorWsAuthInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(MonitorWsAuthInterceptor.class);

    private final AuthTokenService authTokenService;
    private final String monitorSharedSecret;
    private final boolean allowAnonymousLocal;

    public MonitorWsAuthInterceptor(
        AuthTokenService authTokenService,
        @Value("${app.monitor.shared-secret:change-me-dev-secret}") String monitorSharedSecret,
        @Value("${app.monitor.ws-allow-anonymous-local:true}") boolean allowAnonymousLocal
    ) {
        this.authTokenService = authTokenService;
        this.monitorSharedSecret = monitorSharedSecret;
        this.allowAnonymousLocal = allowAnonymousLocal;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String token = httpRequest.getParameter("token");
        if (token != null && !token.isBlank() && authTokenService.getUserIdByToken(token) != null) {
            return true;
        }

        String monitorKey = httpRequest.getParameter("monitorKey");
        if (monitorKey != null && !monitorKey.isBlank() && monitorKey.equals(monitorSharedSecret)) {
            return true;
        }

        if (allowAnonymousLocal && isLocalRequest(httpRequest)) {
            log.debug("Allow local anonymous monitor ws handshake from {}", httpRequest.getRemoteAddr());
            return true;
        }

        log.warn("Reject monitor ws handshake. remoteAddr={}, hasToken={}, hasMonitorKey={}",
            httpRequest.getRemoteAddr(),
            token != null && !token.isBlank(),
            monitorKey != null && !monitorKey.isBlank());
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr);
    }
}
