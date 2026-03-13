package com.root.aishopback.service.impl;

import com.root.aishopback.service.AuthTokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthTokenServiceImpl implements AuthTokenService {
    private static final String TOKEN_KEY_PREFIX = "auth:token:";
    private final StringRedisTemplate redisTemplate;

    public AuthTokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveToken(String token, Long userId, Duration ttl) {
        if (token == null || token.isBlank() || userId == null) return;
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, String.valueOf(userId), ttl);
    }

    @Override
    public Long getUserIdByToken(String token) {
        if (token == null || token.isBlank()) return null;
        String value = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public void removeToken(String token) {
        if (token == null || token.isBlank()) return;
        redisTemplate.delete(TOKEN_KEY_PREFIX + token);
    }
}
