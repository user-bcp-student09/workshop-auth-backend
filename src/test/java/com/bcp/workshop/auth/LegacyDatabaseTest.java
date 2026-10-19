package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseTest {
    private static final String STUDENT_PASSWORD = "FictionalStudentPassword-2026";
    private static final String TRAINER_PASSWORD = "FictionalTrainerPassword-2026";
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void loadSqliteDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    void freshDatabaseStoresOnlyHashedSeedPasswords() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("fresh.db");
        LegacyDatabase database = createDatabase(databaseFile);
        database.initialize();

        UserRecord student = database.findByUsername("student");
        assertEquals("Workshop Student", student.displayName());
        assertNotEquals(STUDENT_PASSWORD, student.passwordHash());
        assertTrue(passwordHasher.verify(STUDENT_PASSWORD, student.passwordHash()));
        assertNull(database.findByUsername("unknown"));
        assertFalse(columns(databaseFile).contains("password"));
    }

    @Test
    void legacyPlaintextRowsAreMigratedOnceAndRemainAuthenticatable() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY, username TEXT UNIQUE, "
                    + "password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO legacy_users VALUES (7, 'legacy-user', "
                    + "'Legacy-Fictional-Password', 'Legacy User', 'STUDENT')");
        }

        LegacyDatabase database = createDatabase(databaseFile);
        database.initialize();
        UserRecord migrated = database.findByUsername("legacy-user");
        String firstHash = migrated.passwordHash();

        database.initialize();
        UserRecord migratedAgain = database.findByUsername("legacy-user");

        assertEquals("Legacy User", migratedAgain.displayName());
        assertEquals(firstHash, migratedAgain.passwordHash());
        assertTrue(passwordHasher.verify("Legacy-Fictional-Password", migratedAgain.passwordHash()));
        assertFalse(columns(databaseFile).contains("password"));
        assertTrue(columns(databaseFile).contains("password_hash"));
    }

    @Test
    void missingSeedPasswordsFailClearly() {
        LegacyDatabase database = new LegacyDatabase(
                "jdbc:sqlite:" + temporaryDirectory.resolve("missing-config.db"),
                " ",
                TRAINER_PASSWORD,
                passwordHasher);

        IllegalStateException error = assertThrows(IllegalStateException.class, database::initialize);
        assertTrue(error.getMessage().contains("AUTH_STUDENT_PASSWORD"));
    }

    private LegacyDatabase createDatabase(Path databaseFile) {
        return new LegacyDatabase(
                "jdbc:sqlite:" + databaseFile,
                STUDENT_PASSWORD,
                TRAINER_PASSWORD,
                passwordHasher);
    }

    private Set<String> columns(Path databaseFile) throws Exception {
        Set<String> columnNames = new HashSet<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columnNames.add(result.getString("name"));
            }
        }
        return columnNames;
    }
}
