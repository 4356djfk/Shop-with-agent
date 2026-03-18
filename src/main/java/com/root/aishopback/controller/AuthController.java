package com.root.aishopback.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.root.aishopback.entity.AppUser;
import com.root.aishopback.mapper.AppUserMapper;
import com.root.aishopback.security.PasswordService;
import com.root.aishopback.service.AuthTokenService;
import com.root.aishopback.service.MonitorClientManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static AuthTokenService staticAuthTokenService;

    private final MonitorClientManager monitorClientManager;
    private final AppUserMapper appUserMapper;
    private final PasswordService passwordService;
    private final AuthTokenService authTokenService;
    private final long tokenTtlDays;

    public AuthController(
        MonitorClientManager monitorClientManager,
        AppUserMapper appUserMapper,
        PasswordService passwordService,
        AuthTokenService authTokenService,
        @Value("${app.auth.token-ttl-days:7}") long tokenTtlDays
    ) {
        this.monitorClientManager = monitorClientManager;
        this.appUserMapper = appUserMapper;
        this.passwordService = passwordService;
        this.authTokenService = authTokenService;
        this.tokenTtlDays = tokenTtlDays;
        staticAuthTokenService = authTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = safe(request.get("username"));
        String password = safe(request.get("password"));
        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("message", "Username and password are required"));
        }

        AppUser dbUser = appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username).last("LIMIT 1")
        );
        if (dbUser == null || dbUser.getPasswordHash() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        boolean validPassword;
        if (passwordService.isBcryptHash(dbUser.getPasswordHash())) {
            validPassword = passwordService.matches(password, dbUser.getPasswordHash());
        } else {
            // Backward compatibility for legacy plaintext rows. Upgrade to BCrypt on successful login.
            validPassword = password.equals(dbUser.getPasswordHash());
            if (validPassword) {
                dbUser.setPasswordHash(passwordService.encode(password));
                appUserMapper.updateById(dbUser);
            }
        }
        if (!validPassword) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        monitorClientManager.startClientForUser(username);
        String token = UUID.randomUUID().toString();
        authTokenService.saveToken(token, dbUser.getId(), Duration.ofDays(Math.max(1, tokenTtlDays)));

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", Map.of(
            "id", dbUser.getId(),
            "username", dbUser.getUsername(),
            "nickname", nullSafe(dbUser.getNickname(), dbUser.getUsername()),
            "avatar", nullSafe(dbUser.getAvatarUrl(), "https://picsum.photos/seed/avatar/100/100"),
            "email", nullSafe(dbUser.getEmail(), dbUser.getUsername() + "@example.com")
        ));
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = safe(request.get("username"));
        String password = safe(request.get("password"));
        if (username.length() < 3 || password.length() < 6) {
            return ResponseEntity.status(400).body(Map.of("message", "Invalid username or password length"));
        }
        AppUser exists = appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username).last("LIMIT 1")
        );
        if (exists != null) {
            return ResponseEntity.status(409).body(Map.of("message", "Username already exists"));
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordService.encode(password));
        user.setNickname(nullSafe(request.get("nickname"), username));
        user.setAvatarUrl("https://picsum.photos/seed/avatar/100/100");
        user.setEmail(nullSafe(request.get("email"), username + "@example.com"));
        user.setStatus("active");
        appUserMapper.insert(user);
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of("message", "Register success")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> request, HttpServletRequest httpServletRequest) {
        String username = request == null ? "" : safe(request.get("username"));
        String token = resolveToken(httpServletRequest);

        // Fallback: if frontend doesn't pass username, resolve it from token to ensure monitor client is stopped.
        if (username.isBlank() && !token.isBlank()) {
            Long userId = authTokenService.getUserIdByToken(token);
            if (userId != null) {
                AppUser user = appUserMapper.selectById(userId);
                if (user != null) {
                    username = safe(user.getUsername());
                }
            }
        }

        if (!username.isBlank()) {
            monitorClientManager.stopClientForUser(username);
        }
        if (!token.isBlank()) {
            authTokenService.removeToken(token);
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "Logout success"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        String token = resolveToken(request);
        Long userId = authTokenService.getUserIdByToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "data", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", nullSafe(user.getNickname(), user.getUsername()),
                "avatar", nullSafe(user.getAvatarUrl(), "https://picsum.photos/seed/avatar/100/100"),
                "email", nullSafe(user.getEmail(), user.getUsername() + "@example.com")
            )
        ));
    }

    public static Long resolveUserIdByToken(String token) {
        if (token == null || token.isBlank()) return null;
        if (staticAuthTokenService == null) return null;
        return staticAuthTokenService.getUserIdByToken(token);
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null) return "";
        if (auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
