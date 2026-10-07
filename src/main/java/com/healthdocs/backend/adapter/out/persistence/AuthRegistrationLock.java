package com.healthdocs.backend.adapter.out.persistence;

import java.sql.PreparedStatement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

/** Transaction-scoped PostgreSQL locks for idempotent registration under concurrent requests. */
@Repository
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class AuthRegistrationLock {

    private final JdbcTemplate jdbc;

    public AuthRegistrationLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(String key) {
        // Advisory locks are released automatically when the surrounding transaction ends.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, key);
                statement.execute();
            }
            return null;
        });
    }
}
