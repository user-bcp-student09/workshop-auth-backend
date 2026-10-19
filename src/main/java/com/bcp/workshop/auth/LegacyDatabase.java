package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class LegacyDatabase implements UserRepository {
    private static final String DEFAULT_DATABASE_URL = "jdbc:sqlite:./data/workshop.db";
    private static final String USERS_TABLE = "legacy_users";

    @Inject
    PasswordHasher passwordHasher;

    @ConfigProperty(name = "auth.database.url", defaultValue = DEFAULT_DATABASE_URL)
    String databaseUrl;

    @ConfigProperty(name = "auth.student.password")
    String studentPassword;

    @ConfigProperty(name = "auth.trainer.password")
    String trainerPassword;

    LegacyDatabase() {}

    LegacyDatabase(String databaseUrl, String studentPassword, String trainerPassword, PasswordHasher passwordHasher) {
        this.databaseUrl = databaseUrl;
        this.studentPassword = studentPassword;
        this.trainerPassword = trainerPassword;
        this.passwordHasher = passwordHasher;
    }

    @PostConstruct
    void initialize() {
        requireConfiguredPassword("AUTH_STUDENT_PASSWORD", studentPassword);
        requireConfiguredPassword("AUTH_TRAINER_PASSWORD", trainerPassword);

        try {
            createDatabaseDirectory();
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                connection.setAutoCommit(false);
                try {
                    createTable(connection);
                    migratePlaintextPasswords(connection);
                    seedUser(connection, "student", studentPassword, "Workshop Student", "STUDENT");
                    seedUser(connection, "trainer", trainerPassword, "Workshop Trainer", "TRAINER");
                    connection.commit();
                } catch (RuntimeException | SQLException error) {
                    connection.rollback();
                    throw error;
                }
            }
        } catch (SQLException | IOException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    @Override
    public UserRecord findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, display_name, role FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new UserRecord(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("password_hash"),
                        result.getString("display_name"),
                        result.getString("role"));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to query authentication database", error);
        }
    }

    private void createTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS legacy_users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE, "
                + "password_hash TEXT NOT NULL, "
                + "display_name TEXT NOT NULL, "
                + "role TEXT NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void migratePlaintextPasswords(Connection connection) throws SQLException {
        Set<String> columns = getColumns(connection);
        if (columns.contains("password")) {
            if (!columns.contains("password_hash")) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE legacy_users ADD COLUMN password_hash TEXT");
                }
            }

            migratePasswordRows(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE legacy_users DROP COLUMN password");
            }
        } else if (!columns.contains("password_hash")) {
            throw new IllegalStateException("Authentication database has no supported password column");
        }
    }

    private Set<String> getColumns(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        return columns;
    }

    private void migratePasswordRows(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, password FROM legacy_users")) {
            while (result.next()) {
                legacyPasswords.add(new LegacyPassword(result.getLong("id"), result.getString("password")));
            }
        }

        String sql = "UPDATE legacy_users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LegacyPassword legacyPassword : legacyPasswords) {
                statement.setString(1, passwordHasher.hash(legacyPassword.value()));
                statement.setLong(2, legacyPassword.id());
                statement.executeUpdate();
            }
        }
    }

    private void seedUser(Connection connection, String username, String password, String displayName, String role)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO legacy_users(username, password_hash, display_name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private void createDatabaseDirectory() throws IOException {
        String sqlitePrefix = "jdbc:sqlite:";
        if (!databaseUrl.startsWith(sqlitePrefix)) {
            return;
        }

        String fileName = databaseUrl.substring(sqlitePrefix.length());
        if (fileName.isBlank() || fileName.equals(":memory:")) {
            return;
        }
        Path parent = Path.of(fileName).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void requireConfiguredPassword(String settingName, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(settingName + " must be configured");
        }
    }

    private record LegacyPassword(long id, String value) {}
}
