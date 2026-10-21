package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthStartupIT {
    private static final String SECRET = "Fictional-Startup-Only-Signing-Key-2026";
    private static final String WRONG_PASSWORD = "Fictional-Startup-Wrong-Password-2026";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path REAL_DATABASE = Path.of("data", "workshop.db");
    private static byte[] originalDatabase;

    @BeforeAll
    static void snapshotRealDatabase() throws IOException {
        originalDatabase = Files.exists(REAL_DATABASE) ? Files.readAllBytes(REAL_DATABASE) : null;
        assertTrue(Files.isRegularFile(Path.of("target", "quarkus-app", "quarkus-run.jar")),
                "Run mvn package quarkus:build before the explicit startup test selector");
    }

    @AfterAll
    static void realDatabaseRemainsUntouched() throws IOException {
        byte[] current = Files.exists(REAL_DATABASE) ? Files.readAllBytes(REAL_DATABASE) : null;
        assertTrue(Arrays.equals(originalDatabase, current), "Startup tests must not change the real database");
    }

    static Stream<Arguments> invalidConfigurations() {
        return Stream.of("dev", "packaged").flatMap(mode -> Stream.of(
                Arguments.of(mode, null), Arguments.of(mode, ""), Arguments.of(mode, "   ")));
    }

    @ParameterizedTest(name = "{index}: {0}, invalid secret configuration")
    @MethodSource("invalidConfigurations")
    void invalidSecretPreventsStartupWithoutFallbackOrSensitiveDiagnostics(String mode, String secret)
            throws Exception {
        try (ServerProcess server = new ServerProcess(mode, secret)) {
            server.awaitConfigurationFailure();
            String logs = server.logs();
            assertTrue(logs.contains("AUTH_TOKEN_SECRET requires explicit configuration"));
            assertFalse(logs.contains("Authentication completed"));
            assertFalse(server.healthy());
            assertSafe(logs, SECRET, TokenServiceTest.TEST_SECRET,
                    AuthCharacterizationResource.STUDENT_PASSWORD, WRONG_PASSWORD);
            if (mode.equals("packaged")) {
                assertTrue(server.process.waitFor(15, TimeUnit.SECONDS), "Failed packaged app must exit");
                assertTrue(server.process.exitValue() != 0);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "packaged"})
    void explicitSecretEnablesHealthyLoginAndSignedTokensWithoutLoggingSensitiveValues(String mode)
            throws Exception {
        try (ServerProcess server = new ServerProcess(mode, SECRET)) {
            server.awaitHealthy();
            HttpResponse<String> success = server.login("student",
                    AuthCharacterizationResource.STUDENT_PASSWORD, " angular-workshop ");
            assertEquals(200, success.statusCode());
            JsonNode body = MAPPER.readTree(success.body());
            String token = body.path("token").textValue();
            assertTrue(token != null && !token.isBlank());
            TokenService verifier = new TokenService(SECRET, MAPPER, Clock.systemUTC());
            TokenService.Claims claims = verifier.verify(token);
            assertEquals("student", claims.sub());
            assertEquals(" angular-workshop ", claims.client());
            assertEquals(3600, claims.exp() - claims.iat());
            assertEquals(3600, body.path("expiresIn").intValue());
            assertEquals("STUDENT", body.path("user").path("role").textValue());
            assertEquals("authenticated-with-legacy-flow", body.path("debug").textValue());
            assertThrows(TokenService.InvalidTokenException.class, () -> verifier.verify(token + "changed"));
            TokenService expired = new TokenService(SECRET, MAPPER,
                    Clock.fixed(java.time.Instant.ofEpochSecond(claims.exp()), java.time.ZoneOffset.UTC));
            assertThrows(TokenService.InvalidTokenException.class, () -> expired.verify(token));
            assertEquals(401, server.login("student", WRONG_PASSWORD, "app").statusCode());
            assertEquals(400, server.login("student",
                    AuthCharacterizationResource.STUDENT_PASSWORD, "").statusCode());
            assertTrue(server.healthy());
            String logs = server.logs();
            assertTrue(logs.contains("Authentication completed"));
            assertSafe(logs, SECRET, token, AuthCharacterizationResource.STUDENT_PASSWORD, WRONG_PASSWORD);
        }
    }

    private static void assertSafe(String logs, String... sensitiveValues) {
        for (String sensitive : sensitiveValues) {
            assertFalse(logs.contains(sensitive), "Child process diagnostics must not disclose sensitive values");
        }
    }

    private static class ServerProcess implements AutoCloseable {
        private final Path directory;
        private final Path output;
        private final Process process;
        private final URI baseUri;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

        ServerProcess(String mode, String secret) throws IOException {
            directory = Files.createTempDirectory(Path.of("target"), "hu01-startup-").toAbsolutePath();
            output = directory.resolve("console.log");
            int port;
            try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
                port = socket.getLocalPort();
            }
            baseUri = URI.create("http://127.0.0.1:" + port);
            List<String> command = new ArrayList<>();
            if (mode.equals("dev")) {
                boolean windows = System.getProperty("os.name").startsWith("Windows");
                if (windows) {
                    command.addAll(List.of("cmd.exe", "/d", "/c"));
                }
                command.add(Path.of(System.getProperty("test.maven.home"), "bin",
                        windows ? "mvn.cmd" : "mvn").toString());
                command.addAll(List.of("-B", "quarkus:dev", "-Ddebug=false", "-Dquarkus.console.enabled=false"));
            } else {
                command.add(Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString());
                command.add("-Dquarkus.profile=prod");
            }
            command.add("-Dquarkus.http.host=127.0.0.1");
            command.add("-Dquarkus.http.port=" + port);
            if (!mode.equals("dev")) {
                command.addAll(List.of("-jar", Path.of("target", "quarkus-app", "quarkus-run.jar")
                        .toAbsolutePath().toString()));
            }
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            Map<String, String> environment = builder.environment();
            environment.remove("AUTH_TOKEN_SECRET");
            environment.remove("JAVA_TOOL_OPTIONS");
            environment.remove("JDK_JAVA_OPTIONS");
            environment.remove("_JAVA_OPTIONS");
            environment.remove("QUARKUS_PROFILE");
            environment.put("JAVA_HOME", System.getProperty("java.home"));
            environment.put("AUTH_DB_URL", "jdbc:sqlite:" + directory.resolve("auth.db"));
            environment.put("QUARKUS_ANALYTICS_DISABLED", "true");
            if (secret != null) {
                environment.put("AUTH_TOKEN_SECRET", secret);
            }
            process = builder.start();
        }

        void awaitConfigurationFailure() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (System.nanoTime() < deadline) {
                if (logs().contains("AUTH_TOKEN_SECRET requires explicit configuration")) {
                    return;
                }
                if (!process.isAlive()) {
                    break;
                }
                process.waitFor(100, TimeUnit.MILLISECONDS);
            }
            throw new AssertionError("Child did not report the required safe configuration failure");
        }

        void awaitHealthy() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (System.nanoTime() < deadline && process.isAlive()) {
                if (healthy()) {
                    return;
                }
                if (logs().contains("AUTH_TOKEN_SECRET requires explicit configuration")) {
                    throw new AssertionError("Explicit test configuration was not accepted");
                }
                process.waitFor(100, TimeUnit.MILLISECONDS);
            }
            throw new AssertionError("Child did not become healthy within the startup deadline");
        }

        boolean healthy() throws InterruptedException {
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("/q/health"))
                        .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.ofString());
                JsonNode body = MAPPER.readTree(response.body());
                return response.statusCode() == 200 && "UP".equals(body.path("status").textValue())
                        && body.path("checks").isArray();
            } catch (IOException error) {
                return false;
            }
        }

        HttpResponse<String> login(String username, String password, String application) throws Exception {
            String json = MAPPER.writeValueAsString(Map.of("username", username,
                    "password", password, "client", application));
            return client.send(HttpRequest.newBuilder(baseUri.resolve("/auth/login"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        }

        String logs() throws IOException {
            return Files.exists(output) ? new String(Files.readAllBytes(output), StandardCharsets.UTF_8) : "";
        }

        @Override
        public void close() throws Exception {
            List<ProcessHandle> descendants = process.descendants().toList();
            for (ProcessHandle child : descendants) {
                child.destroy();
            }
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            for (ProcessHandle child : descendants) {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
                child.onExit().get(10, TimeUnit.SECONDS);
            }
            process.waitFor(10, TimeUnit.SECONDS);
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.equals(directory) && !path.equals(output)) {
                        Files.delete(path);
                    }
                }
            }
        }
    }
}