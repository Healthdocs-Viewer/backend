package com.healthdocs.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TestProfileStartupTest {

    @Test
    void startsWithTheIsolatedTestProfile() {
        // The context start verifies Flyway, the H2 profile and provider validation.
    }
}
