package com.root.aishopback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableCaching
@MapperScan("com.root.aishopback.mapper")
public class AIshopBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIshopBackApplication.class, args);
    }

}
