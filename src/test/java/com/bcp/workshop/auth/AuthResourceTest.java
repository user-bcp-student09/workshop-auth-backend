package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.sql.DriverManager;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(AuthCharacterizationResource.class)
class AuthResourceTest {
    private static final String LOGIN_PATH = "/auth/login";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TokenService tokens;

    @Inject
    LegacyDatabase database;

    @Test
    void logsInWithWorkshopUser() throws Exception {
        assertSuccessfulLogin("student", AuthCharacterizationResource.STUDENT_PASSWORD,
                "Workshop Student", "STUDENT");
    }

    @Test
    void trainerLoginPreservesResponseContract() throws Exception {
        assertSuccessfulLogin("trainer", AuthCharacterizationResource.TRAINER_PASSWORD,
                "Workshop Trainer", "TRAINER");
    }

    @Test
    void displayNameIsEscapedWithoutChangingIdentityOrRole() throws Exception {
        assertSuccessfulLogin("html-student", AuthCharacterizationResource.HTML_PASSWORD,
                "&lt;b&gt;Ana &amp; Luis&lt;/b&gt;", "STUDENT");
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidRequestsPreserveMessagesAndValidationOrder(String request, String message) throws Exception {
        JsonNode response = postLogin(request, 400);
        assertEquals(Set.of("message"), fieldNames(response));
        assertEquals(message, response.path("message").textValue());
        assertFalse(response.has("token"));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("null", "Request is null"),
                Arguments.of("{}", "Username is required"),
                Arguments.of("{\"username\":null,\"password\":null,\"client\":null}", "Username is required"),
                Arguments.of("{\"username\":\"\",\"password\":\"\",\"client\":\"\"}", "Username is required"),
                Arguments.of("{\"username\":\"   \",\"password\":\"   \",\"client\":\"   \"}", "Username is required"),
                Arguments.of("{\"username\":\"student\"}", "Password is required"),
                Arguments.of("{\"username\":\"student\",\"password\":null,\"client\":null}", "Password is required"),
                Arguments.of("{\"username\":\"student\",\"password\":\"\",\"client\":\"\"}", "Password is required"),
                Arguments.of("{\"username\":\"student\",\"password\":\"   \",\"client\":\"   \"}", "Password is required"),
                Arguments.of("{\"username\":null,\"password\":\"wrong\",\"client\":{}}", "Username is required"),
                Arguments.of("{\"username\":\"student\",\"password\":null,\"client\":[]}", "Password is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-existe", "student"})
    void invalidCredentialsReturnOnlyUnauthorizedMessage(String username) throws Exception {
        ObjectNode request = loginRequest(username, "FictionalIncorrectPassword-2026");
        JsonNode response = postLogin(request.toString(), 401);
        assertEquals(Set.of("message"), fieldNames(response));
        assertEquals("Invalid user " + username + " or password", response.path("message").textValue());
        assertFalse(response.has("token"));
        assertFalse(response.has("expiresIn"));
        assertFalse(response.has("user"));
    }

    @Test
    void healthRemainsPublicWithRuntimeDefinedChecks() throws Exception {
        String body = given().auth().none().accept("application/json")
                .when().get("/q/health")
                .then().statusCode(200).contentType("application/json")
                .extract().asString();
        JsonNode response = objectMapper.readTree(body);
        assertEquals("UP", response.path("status").textValue());
        assertTrue(response.path("checks").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"client\":null}", "{\"client\":\"\"}", "{\"client\":\"   \"}",
            "{\"client\":123}", "{\"client\":true}", "{\"client\":{}}", "{\"client\":[]}"})
    void invalidClientIsRejectedBeforeCheckingCredentials(String clientFields) throws Exception {
        ObjectNode request = loginRequest("no-existe", "FictionalWrongPassword-2026");
        request.remove("client");
        request.setAll((ObjectNode) objectMapper.readTree(clientFields));
        JsonNode response = postLogin(request.toString(), 400);
        assertEquals(Set.of("message"), fieldNames(response));
        assertEquals("Client is required", response.path("message").textValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"angular-workshop", "python-workshop", " angular-workshop "})
    void signedTokensPreserveEachClientAndOneHourValidity(String client) throws Exception {
        long beforeLogin = Instant.now().getEpochSecond();
        ObjectNode request = loginRequest("student", AuthCharacterizationResource.STUDENT_PASSWORD);
        request.put("client", client);
        JsonNode response = postLogin(request.toString(), 200);
        long afterLogin = Instant.now().getEpochSecond();
        TokenService.Claims claims = tokens.verify(response.path("token").textValue());
        assertEquals("student", claims.sub());
        assertEquals(client, claims.client());
        assertEquals(3600, claims.exp() - claims.iat());
        assertEquals(3600, response.path("expiresIn").intValue());
        assertTrue(claims.iat() >= beforeLogin && claims.iat() <= afterLogin);
    }

    @Test
    void responseTimingFailureReturns500WithoutSuccessfulFields() throws Exception {
        TokenServiceTest.AdjustableClock clock = new TokenServiceTest.AdjustableClock();
        TokenService delayed = new TokenService(TokenServiceTest.TEST_SECRET, objectMapper, clock) {
            @Override
            public IssuedToken issue(String subject, String client) {
                IssuedToken issued = super.issue(subject, client);
                clock.now = clock.now.plusSeconds(2);
                return issued;
            }
        };
        QuarkusMock.installMockForType(delayed, TokenService.class);
        try (TestLogCapture capture = new TestLogCapture()) {
            JsonNode response = postLogin(loginRequest("student",
                AuthCharacterizationResource.STUDENT_PASSWORD).toString(), 500);
            assertEquals(Set.of("message", "exception"), fieldNames(response));
            assertEquals("Token response timing failed", response.path("message").textValue());
            assertEquals("java.lang.IllegalStateException", response.path("exception").textValue());
            assertFalse(capture.text().contains("Authentication completed"));
        }
    }

    @Test
    void unexpectedErrorPreservesLegacyContractOverHttp() throws Exception {
        QuarkusMock.installMockForType(new AuthService() {
            @Override
            public AuthenticationResult authenticate(String username, String password, String client) {
                throw new RuntimeException("Test failure");
            }
        }, AuthService.class);
        JsonNode response = postLogin(loginRequest("student",
                AuthCharacterizationResource.STUDENT_PASSWORD).toString(), 500);
        assertEquals(Set.of("message", "exception"), fieldNames(response));
        assertEquals("Test failure", response.path("message").textValue());
        assertEquals("java.lang.RuntimeException", response.path("exception").textValue());
    }

    @Test
    void loginAndTokenFailuresKeepLogsAndSensitiveResponsesRedacted() throws Exception {
        String[] emitted = new String[1];
        String wrongPassword = "Fictional-Wrong-Password-Sentinel";
        try (TestLogCapture capture = new TestLogCapture()) {
            JsonNode success = postLogin(loginRequest("student",
                    AuthCharacterizationResource.STUDENT_PASSWORD).toString(), 200);
            String successfulToken = success.path("token").textValue();
            postLogin(loginRequest("student", wrongPassword).toString(), 401);
            postLogin("{}", 400);
            TokenService issuer = new TokenService(TokenServiceTest.TEST_SECRET, objectMapper,
                    Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
            String expiredToken = issuer.issue("student", "app").token();
            TokenService expiredVerifier = new TokenService(TokenServiceTest.TEST_SECRET, objectMapper,
                    Clock.fixed(Instant.parse("2026-10-21T13:00:00Z"), ZoneOffset.UTC));
            assertThrows(TokenService.InvalidTokenException.class, () -> expiredVerifier.verify(expiredToken));
            assertThrows(TokenService.InvalidTokenException.class, () -> issuer.verify(expiredToken + "changed"));
            assertThrows(IllegalStateException.class,
                    () -> new TokenConfiguration(java.util.Optional.of("   "), objectMapper));
            TokenService failing = new TokenService(TokenServiceTest.TEST_SECRET, objectMapper, Clock.systemUTC()) {
                @Override
                public void validateForResponse(IssuedToken issued) {
                    emitted[0] = issued.token();
                    throw new IllegalStateException("Controlled failure " + TokenServiceTest.TEST_SECRET
                            + " " + AuthCharacterizationResource.STUDENT_PASSWORD + " " + issued.token());
                }
            };
            QuarkusMock.installMockForType(failing, TokenService.class);
            JsonNode error = postLogin(loginRequest("student",
                    AuthCharacterizationResource.STUDENT_PASSWORD).toString(), 500);
            assertEquals(Set.of("message", "exception"), fieldNames(error));
            assertEquals("java.lang.IllegalStateException", error.path("exception").textValue());
            String logs = capture.text();
            for (String sensitive : new String[] {TokenServiceTest.TEST_SECRET,
                    AuthCharacterizationResource.STUDENT_PASSWORD, wrongPassword, successfulToken,
                    expiredToken, emitted[0]}) {
                assertFalse(logs.contains(sensitive), "Sensitive test value must not appear in logs");
                assertFalse(error.toString().contains(sensitive), "Sensitive test value must not appear in errors");
            }
            assertTrue(logs.contains("Authentication failed"));
            assertTrue(logs.contains("Token rejected"));
            assertTrue(logs.contains("AUTH_TOKEN_SECRET requires explicit configuration"));
        }
    }

    @Test
    void databaseFailuresRemainIdentifiableWithoutSqlOrCredentialTraces() throws Exception {
        String username = "Fictional-Sensitive-User'";
        String password = "Fictional-Sensitive-Database-Password";
        try (TestLogCapture capture = new TestLogCapture();
             var connection = DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
             var statement = connection.createStatement()) {
            assertNull(database.findByCredentials(username, password));
            statement.executeUpdate("CREATE TRIGGER test_only_initialization_failure BEFORE INSERT ON legacy_users BEGIN SELECT RAISE(ABORT, 'Fictional-Sensitive-Db-Sentinel'); END");
            try {
                database.initialize();
            } finally {
                statement.executeUpdate("DROP TRIGGER test_only_initialization_failure");
            }
            String logs = capture.text();
            assertTrue(logs.contains("Authentication database query failed"));
            assertTrue(logs.contains("Authentication database initialization failed"));
            for (String sensitive : new String[] {username, password, "Fictional-Sensitive-Db-Sentinel",
                    "SELECT id", "INSERT OR IGNORE", "SQLException"}) {
                assertFalse(logs.contains(sensitive), "Database diagnostics must not disclose sensitive details");
            }
        }
        postLogin(loginRequest("student", AuthCharacterizationResource.STUDENT_PASSWORD).toString(), 200);
    }

    private void assertSuccessfulLogin(String username, String password, String displayName, String role)
            throws Exception {
        JsonNode response = postLogin(loginRequest(username, password).toString(), 200);
        assertEquals(Set.of("token", "expiresIn", "user", "debug"), fieldNames(response));
        assertTrue(response.path("token").isTextual());
        assertFalse(response.path("token").textValue().isBlank());
        assertTrue(response.path("expiresIn").isIntegralNumber());
        assertEquals(3600, response.path("expiresIn").intValue());
        JsonNode user = response.path("user");
        assertTrue(user.isObject());
        assertEquals(Set.of("username", "displayName", "role"), fieldNames(user));
        assertEquals(username, user.path("username").textValue());
        assertEquals(displayName, user.path("displayName").textValue());
        assertEquals(role, user.path("role").textValue());
        assertEquals("authenticated-with-legacy-flow", response.path("debug").textValue());
    }

    private ObjectNode loginRequest(String username, String password) {
        return objectMapper.createObjectNode().put("username", username)
                .put("password", password).put("client", "angular-workshop");
    }

    private JsonNode postLogin(String request, int status) throws Exception {
        String body = given().auth().none().contentType("application/json").body(request)
                .when().post(LOGIN_PATH)
                .then().statusCode(status).contentType("application/json")
                .extract().asString();
        return objectMapper.readTree(body);
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

}
