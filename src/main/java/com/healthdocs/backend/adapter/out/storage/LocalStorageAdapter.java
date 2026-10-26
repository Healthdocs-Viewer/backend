package com.healthdocs.backend.adapter.out.storage;

import com.healthdocs.backend.application.port.out.StoragePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class LocalStorageAdapter implements StoragePort {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageAdapter.class);

    private final Clock clock;
    private final Path root;
    private final String baseUrl;
    private final Set<String> knownObjects = ConcurrentHashMap.newKeySet();
    private final Map<String, UploadGrant> grants = new ConcurrentHashMap<>();

    public LocalStorageAdapter(Clock clock, InternalFlowProperties properties) {
        this.clock = clock;
        this.root = Path.of(properties.localStoragePath()).toAbsolutePath().normalize();
        this.baseUrl = properties.baseUrl().replaceAll("/$", "");
    }

    @Override
    public UploadUrlResult generateDownloadUrl(String objectKey, String contentType, int ttlMinutes) {
        Instant expiresAt = clock.instant().plusSeconds(ttlMinutes * 60L);
        String token = UUID.randomUUID().toString();
        grants.put(token, new UploadGrant(objectKey, contentType, expiresAt));
        String encodedKey = UriUtils.encodePath(objectKey, java.nio.charset.StandardCharsets.UTF_8);
        log.info("Generated private download grant objectKey={}, expiresAt={}", objectKey, expiresAt);
        return new UploadUrlResult(baseUrl + "/api/v1/dev/downloads/" + encodedKey
                + "?expires=" + expiresAt.toEpochMilli() + "&token=" + token, expiresAt);
    }

    @Override
    public UploadUrlResult generateUploadUrl(String objectKey, String contentType, int ttlMinutes) {
        Instant expiresAt = clock.instant().plusSeconds(ttlMinutes * 60L);
        String token = UUID.randomUUID().toString();
        grants.put(token, new UploadGrant(objectKey, contentType, expiresAt));
        String encodedKey = UriUtils.encodePath(objectKey, java.nio.charset.StandardCharsets.UTF_8);
        String uploadUrl = baseUrl + "/api/v1/dev/uploads/" + encodedKey
                + "?expires=" + expiresAt.toEpochMilli()
                + "&token=" + token;
        return new UploadUrlResult(uploadUrl, expiresAt);
    }

    @Override
    public boolean objectExists(String objectKey) {
        return knownObjects.contains(objectKey) || Files.isRegularFile(safePath(objectKey));
    }

    public void markObjectStored(String objectKey) {
        knownObjects.add(objectKey);
    }

    public boolean storeObject(String objectKey, String token, String contentType, byte[] bytes) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..") || objectKey.startsWith("/")) {
            return false;
        }
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        UploadGrant grant = Optional.ofNullable(grants.get(token)).orElse(null);
        if (grant == null || clock.instant().isAfter(grant.expiresAt())) {
            return false;
        }
        if (!grant.objectKey().equals(objectKey)) {
            return false;
        }
        if (contentType != null
                && !contentType.isBlank()
                && !mediaTypeOnly(grant.contentType()).equals(mediaTypeOnly(contentType))) {
            return false;
        }
        try {
            Path target = safePath(objectKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            knownObjects.add(objectKey);
            log.info("Stored objectKey={}, contentType={}, sizeBytes={}", objectKey, contentType, bytes.length);
        } catch (java.io.IOException | RuntimeException ex) {
            return false;
        }
        grants.remove(token);
        return true;
    }

    public byte[] readObject(String objectKey, String token) {
        UploadGrant grant = Optional.ofNullable(grants.get(token)).orElse(null);
        if (grant == null || clock.instant().isAfter(grant.expiresAt()) || !grant.objectKey().equals(objectKey)) return null;
        try {
            byte[] bytes = Files.readAllBytes(safePath(objectKey));
            log.info("Read objectKey={}, sizeBytes={}", objectKey, bytes.length);
            return bytes;
        } catch (java.io.IOException | RuntimeException ex) { log.warn("Failed to read objectKey={}", objectKey, ex); return null; }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            Files.deleteIfExists(safePath(objectKey));
            knownObjects.remove(objectKey);
        } catch (java.io.IOException | RuntimeException ex) {
            log.warn("Failed to delete objectKey={}", objectKey, ex);
        }
    }

    private Path safePath(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root) || objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("Invalid object key");
        return target;
    }

    private record UploadGrant(String objectKey, String contentType, Instant expiresAt) {
    }

    private static String mediaTypeOnly(String contentType) {
        return contentType.split(";", 2)[0].trim();
    }
}
