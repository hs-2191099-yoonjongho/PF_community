package com.example.community;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test","local"})
class CommunityApplicationTests {
    @Test
    void contextLoads() {}
}
