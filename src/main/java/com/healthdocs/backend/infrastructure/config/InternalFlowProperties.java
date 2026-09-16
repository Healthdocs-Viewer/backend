package com.healthdocs.backend.infrastructure.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "healthdocs.internal")
public record InternalFlowProperties(String localStoragePath, long resultMaxSizeBytes,
        String resultAllowedMimeTypes, int uploadTtlMinutes, int downloadTtlMinutes, String baseUrl) {
    public Set<String> allowedMimeTypes() { return Arrays.stream(resultAllowedMimeTypes.split(","))
            .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet()); }
}
