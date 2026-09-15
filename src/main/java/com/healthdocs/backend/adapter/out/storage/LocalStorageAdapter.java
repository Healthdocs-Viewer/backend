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

@Component
public class LocalStorageAdapter implements StoragePort {

    private final Clock clock;
    private final Set<String> knownObjects = ConcurrentHashMap.newKeySet();
    private final Map<String, UploadGrant> grants = new ConcurrentHashMap<>();

    public LocalStorageAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public UploadUrlResult generateUploadUrl(String objectKey, String contentType, int ttlMinutes) {
        Instant expiresAt = clock.instant().plusSeconds(ttlMinutes * 60L);
        String token = UUID.randomUUID().toString();
        grants.put(token, new UploadGrant(objectKey, contentType, expiresAt));
        String encodedKey = UriUtils.encodePath(objectKey, java.nio.charset.StandardCharsets.UTF_8);
        String uploadUrl = "http://localhost:8080/api/v1/dev/uploads/" + encodedKey
                + "?expires=" + expiresAt.toEpochMilli()
                + "&token=" + token;
        return new UploadUrlResult(uploadUrl, expiresAt);
    }

    @Override
    public boolean objectExists(String objectKey) {
        return knownObjects.contains(objectKey);
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
        knownObjects.add(objectKey);
        grants.remove(token);
        return true;
    }

    private record UploadGrant(String objectKey, String contentType, Instant expiresAt) {
    }

    private static String mediaTypeOnly(String contentType) {
        return contentType.split(";", 2)[0].trim();
    }
}
