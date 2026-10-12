package com.example.realtimechatonline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "MAIL_HOST=localhost",
        "MAIL_PORT=587",
        "MAIL_USERNAME=test",
        "MAIL_PASSWORD=test",
        "JWT_SECRET=thisismocksecretthisismocksecret1234567890",
        "GOOGLE_CLIENT_ID=test",
        "GOOGLE_CLIENT_SECRET=test"
})
class RealtimeChatOnlineApplicationTests {

    @Test
    void contextLoads() {
    }
}
