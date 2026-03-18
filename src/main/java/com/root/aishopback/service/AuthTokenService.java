package com.root.aishopback.service;

import java.time.Duration;
import java.util.Set;

public interface AuthTokenService {
    void saveToken(String token, Long userId, Duration ttl);
    Long getUserIdByToken(String token);
    void removeToken(String token);
    Set<Long> listActiveUserIds();
}
