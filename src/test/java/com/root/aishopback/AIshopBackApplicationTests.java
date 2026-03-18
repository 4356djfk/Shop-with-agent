package com.root.aishopback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "app.monitor.enabled=false",
    "app.frontend.auto-start=false"
})
class AIshopBackApplicationTests {

    @Test
    void contextLoads() {
    }

}
