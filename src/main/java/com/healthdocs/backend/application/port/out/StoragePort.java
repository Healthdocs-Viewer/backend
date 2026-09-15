package com.healthdocs.backend.application.port.out;

import java.time.Instant;

public interface StoragePort {

    UploadUrlResult generateUploadUrl(String objectKey, String contentType, int ttlMinutes);

    boolean objectExists(String objectKey);

    record UploadUrlResult(String uploadUrl, Instant expiresAt) {
    }
}
