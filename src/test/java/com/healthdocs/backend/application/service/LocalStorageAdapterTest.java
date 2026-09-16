package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.storage.LocalStorageAdapter;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageAdapterTest {
    @Test
    void storesAndReadsObjectOnlyThroughValidGrant() throws Exception {
        Path root = Files.createTempDirectory("healthdocs-storage-");
        var storage = new LocalStorageAdapter(Clock.fixed(Instant.parse("2026-09-16T20:00:00Z"), ZoneOffset.UTC),
                new InternalFlowProperties(root.toString(), 1000, "application/pdf", 15, 10, "http://localhost:8080"));
        var upload = storage.generateUploadUrl("result/case/file.pdf", "application/pdf", 15);
        String token = upload.uploadUrl().substring(upload.uploadUrl().indexOf("token=") + 6);
        assertTrue(storage.storeObject("result/case/file.pdf", token, "application/pdf", new byte[]{1, 2, 3}));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("result/case/file.pdf")));
        assertFalse(storage.storeObject("result/case/file.pdf", token, "application/pdf", new byte[]{4}));
    }

    @Test
    void rejectsTraversal() {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "healthdocs-storage-test");
        var storage = new LocalStorageAdapter(Clock.systemUTC(),
                new InternalFlowProperties(root.toString(), 1000, "application/pdf", 1, 1, "http://localhost:8080"));
        var url = storage.generateUploadUrl("../private.pdf", "application/pdf", 1);
        String token = url.uploadUrl().substring(url.uploadUrl().indexOf("token=") + 6);
        assertFalse(storage.storeObject("../private.pdf", token, "application/pdf", new byte[]{1}));
    }
}
