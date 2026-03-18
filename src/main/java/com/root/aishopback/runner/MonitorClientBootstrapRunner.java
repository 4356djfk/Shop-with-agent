package com.root.aishopback.runner;

import com.root.aishopback.entity.AppUser;
import com.root.aishopback.mapper.AppUserMapper;
import com.root.aishopback.service.AuthTokenService;
import com.root.aishopback.service.MonitorClientManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitorClientBootstrapRunner implements CommandLineRunner {

    private final MonitorClientManager monitorClientManager;
    private final AuthTokenService authTokenService;
    private final AppUserMapper appUserMapper;

    public MonitorClientBootstrapRunner(
        MonitorClientManager monitorClientManager,
        AuthTokenService authTokenService,
        AppUserMapper appUserMapper
    ) {
        this.monitorClientManager = monitorClientManager;
        this.authTokenService = authTokenService;
        this.appUserMapper = appUserMapper;
    }

    @Override
    public void run(String... args) {
        Set<Long> userIds = authTokenService.listActiveUserIds();
        if (userIds.isEmpty()) {
            System.out.println("[MonitorBootstrap] No active Redis tokens found, skip preloading monitor clients.");
            return;
        }

        List<AppUser> users = appUserMapper.selectBatchIds(userIds);
        int started = 0;
        for (AppUser user : users) {
            if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
                continue;
            }
            monitorClientManager.startClientForUser(user.getUsername());
            started++;
        }
        System.out.println("[MonitorBootstrap] Preloaded monitor clients for " + started + " active users from Redis tokens.");
    }
}
