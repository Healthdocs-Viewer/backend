package com.healthdocs.backend.infrastructure.config;

import com.healthdocs.backend.application.service.AuthIdentityMigrationService;
import com.healthdocs.backend.application.service.AuthIdentityMigrationService.LinkStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** One-shot, dry-run-first importer for approved account-to-identity mappings. */
@Component
@Profile("auth-migration")
public class AuthIdentityMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthIdentityMigrationRunner.class);
    private static final String HEADER = "app_user_id,access_token,verified_by,verification_reference";

    private final AuthIdentityMigrationService migration;
    private final Path importFile;
    private final boolean apply;

    public AuthIdentityMigrationRunner(
            AuthIdentityMigrationService migration,
            @Value("${AUTH_MIGRATION_FILE:}") String importFile,
            @Value("${AUTH_MIGRATION_APPLY:false}") boolean apply) {
        this.migration = migration;
        this.importFile = importFile == null || importFile.isBlank() ? null : Path.of(importFile);
        this.apply = apply;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (importFile == null) {
            throw new IllegalStateException("AUTH_MIGRATION_FILE must point to the temporary approved mapping file");
        }
        if (!Files.isRegularFile(importFile) || Files.isSymbolicLink(importFile)) {
            throw new IllegalStateException("AUTH_MIGRATION_FILE must be a regular, non-symbolic-link file");
        }

        List<Mapping> mappings = readMappings(importFile);
        List<AuthIdentityMigrationService.MigrationPreview> previews = new ArrayList<>();
        Set<String> seenIdentities = new HashSet<>();
        for (int index = 0; index < mappings.size(); index++) {
            Mapping mapping = mappings.get(index);
            try {
                var preview = migration.preview(mapping.internalUserId(), mapping.accessToken());
                if (!seenIdentities.add(preview.identityFingerprint())) {
                    throw new IllegalArgumentException("Supabase identity appears more than once in the import file");
                }
                previews.add(preview);
            } catch (RuntimeException exception) {
                // Errors are intentionally sanitized; tokens are never printed.
                throw new IllegalStateException("Mapping row " + (index + 2) + " failed preflight validation", exception);
            }
        }

        long alreadyLinked = previews.stream().filter(row -> row.status() == LinkStatus.ALREADY_LINKED).count();
        long toLink = previews.size() - alreadyLinked;
        for (var preview : previews) {
            log.info("Auth identity mapping preflight: app_user_id={} status={}",
                    preview.internalUserId(), preview.status());
        }
        if (!apply) {
            log.info("Auth identity migration dry run passed: {} rows, {} already linked, {} ready to link. No rows changed.",
                    previews.size(), alreadyLinked, toLink);
            return;
        }

        for (Mapping mapping : mappings) {
            migration.link(
                    mapping.internalUserId(),
                    mapping.accessToken(),
                    mapping.verifiedBy(),
                    mapping.verificationReference());
        }
        log.info("Auth identity migration applied: {} rows, {} already linked, {} linked.",
                previews.size(), alreadyLinked, toLink);
    }

    private static List<Mapping> readMappings(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0).trim())) {
            throw new IllegalArgumentException("Import file must start with the documented header");
        }
        List<Mapping> mappings = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] fields = line.split(",", -1);
            if (fields.length != 4) {
                throw new IllegalArgumentException("Mapping row " + (index + 1) + " must have exactly four columns");
            }
            try {
                if (fields[1].isBlank() || !fields[1].equals(fields[1].trim())) {
                    throw new IllegalArgumentException("access_token is required without surrounding whitespace");
                }
                mappings.add(new Mapping(
                        UUID.fromString(fields[0].trim()),
                        fields[1].trim(),
                        required(fields[2], "verified_by"),
                        required(fields[3], "verification_reference")));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Mapping row " + (index + 1) + " is malformed", exception);
            }
        }
        if (mappings.isEmpty()) throw new IllegalArgumentException("Import file has no mapping rows");
        return mappings;
    }

    private static String required(String value, String column) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 255) {
            throw new IllegalArgumentException(column + " is required without surrounding whitespace");
        }
        return value;
    }

    private record Mapping(UUID internalUserId, String accessToken, String verifiedBy, String verificationReference) {
    }
}
