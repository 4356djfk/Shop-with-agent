package com.root.aishopback.util;

import com.root.aishopback.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;

public class UserContextUtil {
    private UserContextUtil() {}

    public static long resolveUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long tokenUserId = AuthController.resolveUserIdByToken(auth.substring(7).trim());
            if (tokenUserId != null) {
                return tokenUserId;
            }
            throw new SecurityException("登录已失效，请重新登录");
        }

        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new SecurityException("未登录，请先登录");
    }
}
