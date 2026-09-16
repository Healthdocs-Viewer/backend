package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.adapter.out.storage.LocalStorageAdapter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

@RestController
@Profile({"local", "dev", "test"})
public class LocalDevUploadController {

    private final LocalStorageAdapter storage;

    public LocalDevUploadController(LocalStorageAdapter storage) {
        this.storage = storage;
    }

    @PutMapping("/api/v1/dev/uploads/{*objectKey}")
    public ResponseEntity<Void> upload(
            @PathVariable String objectKey,
            @RequestParam String token,
            @RequestHeader(name = "Content-Type", required = false) String contentType,
            HttpServletRequest request) throws IOException {
        String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        byte[] bytes = request.getInputStream().readAllBytes();
        boolean stored = storage.storeObject(
                normalizedKey,
                token,
                contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType,
                bytes);
        return stored
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/api/v1/dev/downloads/{*objectKey}")
    public ResponseEntity<ByteArrayResource> download(
            @PathVariable String objectKey, @RequestParam String token) {
        String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        byte[] bytes = storage.readObject(normalizedKey, token);
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(new ByteArrayResource(bytes));
    }
}
