package com.root.aishopback.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Order(100)
@ConditionalOnProperty(prefix = "app.frontend", name = "auto-start", havingValue = "true")
public class FrontendAutoStarter implements CommandLineRunner {

    private final String frontendDirPath;

    public FrontendAutoStarter(@Value("${app.frontend.dir:e:\\\\Code\\\\Vue\\\\b\\\\detect-end}") String frontendDirPath) {
        this.frontendDirPath = frontendDirPath;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting frontend automatic execution...");

        File frontendDir = new File(frontendDirPath);
        if (!frontendDir.exists() || !frontendDir.isDirectory()) {
            System.err.println("Frontend directory not found at: " + frontendDirPath);
            return;
        }

        try {
            // Using windows cmd to start a new visible window running the frontend
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "start", "\"Frontend Server\"", "cmd.exe", "/k", "npm run serve");
            builder.directory(frontendDir);
            
            // We only trigger it and do not block the thread.
            builder.start();
            System.out.println("Frontend process (npm run serve) successfully dispatched.");

            // Give it 5 seconds to warm up, then explicitly open the browser
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    new ProcessBuilder("cmd.exe", "/c", "start", "http://localhost:8081").start();
                    System.out.println("Browser dispatched to open http://localhost:8081");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            System.err.println("Failed to start frontend automatically: ");
            e.printStackTrace();
        }
    }
}
