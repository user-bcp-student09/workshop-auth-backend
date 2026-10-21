package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Map;

public class AuthCharacterizationResource implements QuarkusTestResourceLifecycleManager {
    static final String STUDENT_PASSWORD = "Workshop123!";
    static final String TRAINER_PASSWORD = "Trainer123!";
    static final String HTML_PASSWORD = "FictionalHtmlPassword-2026";

    private final Path realDatabase = Path.of("data", "workshop.db");
    private Path testDatabase;
    private byte[] originalDatabase;
    private boolean ownsTestDatabase;

    @Override
    public Map<String, String> start() {
        try {
            originalDatabase = Files.exists(realDatabase) ? Files.readAllBytes(realDatabase) : null;
            testDatabase = Path.of("target", "auth-characterization.db").toAbsolutePath().normalize();
            String databaseUrl = System.getenv("AUTH_DB_URL");
            if (databaseUrl == null || !databaseUrl.startsWith("jdbc:sqlite:")
                    || !Path.of(databaseUrl.substring("jdbc:sqlite:".length()))
                            .toAbsolutePath().normalize().equals(testDatabase)) {
                throw new IllegalStateException("Tests require the isolated Surefire AUTH_DB_URL");
            }
            Files.createFile(testDatabase);
            ownsTestDatabase = true;
            try (var connection = DriverManager.getConnection(databaseUrl);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
                try (var insert = connection.prepareStatement(
                        "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
                    String[][] users = {
                            {"student", STUDENT_PASSWORD, "Workshop Student", "STUDENT"},
                            {"trainer", TRAINER_PASSWORD, "Workshop Trainer", "TRAINER"},
                            {"html-student", HTML_PASSWORD, "<b>Ana & Luis</b>", "STUDENT"}
                    };
                    for (String[] user : users) {
                        for (int fieldIndex = 0; fieldIndex < user.length; fieldIndex++) {
                            insert.setString(fieldIndex + 1, user[fieldIndex]);
                        }
                        insert.executeUpdate();
                    }
                }
            }
            return Map.of();
        } catch (Exception error) {
            if (ownsTestDatabase) {
                stop();
            }
            throw new IllegalStateException("Could not initialize isolated authentication fixtures", error);
        }
    }

    @Override
    public void stop() {
        try {
            if (ownsTestDatabase) {
                Files.delete(testDatabase);
                ownsTestDatabase = false;
            }
            byte[] currentDatabase = Files.exists(realDatabase) ? Files.readAllBytes(realDatabase) : null;
            if (!Arrays.equals(originalDatabase, currentDatabase)) {
                throw new IllegalStateException("Characterization changed data/workshop.db");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not safely finish isolated authentication fixtures", error);
        }
    }
}