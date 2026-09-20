package com.healthdocs.backend.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties({InternalFlowProperties.class, SqsNotificationProperties.class})
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
