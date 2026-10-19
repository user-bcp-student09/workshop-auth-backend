package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.emptyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String LOGIN_PATH = "/auth/login";
    private static final String STUDENT_PASSWORD = "FictionalStudentPassword-2026";

    @Inject
    TokenService tokenService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void successfulLoginPreservesResponseContractAndReturnsSignedJwt() throws Exception {
        long beforeLogin = Instant.now().getEpochSecond();
        String token = given()
                .contentType("application/json")
                .accept("application/json")
                .body("{\"username\":\"student\",\"password\":\"FictionalStudentPassword-2026\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("size()", is(4))
                .body("user.size()", is(3))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("user.passwordHash", nullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("token", notNullValue())
                .extract().path("token");

        TokenService.TokenClaims claims = tokenService.verify(token);
        assertEquals("student", claims.subject());
        assertEquals("STUDENT", claims.role());
        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(4, payload.size());
        assertEquals(3600, payload.get("exp").longValue() - payload.get("iat").longValue());
        assertTrue(payload.get("iat").longValue() >= beforeLogin);
        assertTrue(payload.get("iat").longValue() <= Instant.now().getEpochSecond());
    }

    @Test
    void successfulResponseEscapesDisplayNameAndPreservesContract() {
        UserRecord user = new UserRecord(
                1L, "student", "unused-test-hash", "Student & <Trainer> \"Demo\"", "STUDENT");
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public AuthenticationResult authenticate(String username, String password) {
                return new AuthenticationResult(user, "fictitious-test-token", 3600);
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = STUDENT_PASSWORD;

        try (Response response = resource.login(request)) {
            assertEquals(200, response.getStatus());
            LoginResponse expected = new LoginResponse(
                    "fictitious-test-token",
                    3600,
                    new LoginResponse.UserDto(
                            "student", "Student &amp; &lt;Trainer&gt; &quot;Demo&quot;", "STUDENT"),
                    "authenticated-with-legacy-flow");
            assertEquals(expected, response.getEntity());
        }
    }

    @Test
    void invalidPasswordPreservesUnauthorizedResponse() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void sqlSyntaxInUsernameCannotChangeAuthenticationQuery() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student' -- \",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidRequestsPreserveValidationResponses(String request, String message) {
        given().contentType("application/json").body(request)
                .when().post(LOGIN_PATH)
                .then().statusCode(400).contentType("application/json")
                .body("size()", is(1))
                .body("message", is(message));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("null", "Request is null"),
                Arguments.of("{}", "Username is required"),
                Arguments.of("{\"username\":null}", "Username is required"),
                Arguments.of("{\"username\":\"\"}", "Username is required"),
                Arguments.of("{\"username\":\"  \"}", "Username is required"),
                Arguments.of("{\"username\":\"student\"}", "Password is required"),
                Arguments.of("{\"username\":\"student\",\"password\":null}", "Password is required"),
                Arguments.of("{\"username\":\"student\",\"password\":\"\"}", "Password is required"),
                Arguments.of("{\"username\":\"student\",\"password\":\"  \"}", "Password is required"));
    }

    @Test
    void malformedJsonReturnsEmptyBadRequest() {
        given().contentType("application/json").body("{")
                .when().post(LOGIN_PATH)
                .then().statusCode(400)
                .header("Content-Type", nullValue())
                .body(emptyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"client\":null", ",\"client\":\"ignored\",\"unknownField\":true"})
    void optionalClientAndUnknownFieldsDoNotAffectAuthentication(String extraFields) throws Exception {
        String request = "{\"username\":\"student\",\"password\":\"" + STUDENT_PASSWORD + "\"" + extraFields + "}";
        String token = given().contentType("application/json").body(request)
                .when().post(LOGIN_PATH)
                .then().statusCode(200).body("user.username", is("student"))
                .extract().path("token");

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertTrue(!payload.has("client"));
        assertEquals("student", tokenService.verify(token).subject());
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void invalidCredentialsAreNotTrimmed(String username, String password) {
        given().contentType("application/json").body(Map.of("username", username, "password", password))
                .when().post(LOGIN_PATH)
                .then().statusCode(401)
                .body("message", is("Invalid user " + username + " or password"));
    }

    static Stream<Arguments> invalidCredentials() {
        return Stream.of(
                Arguments.of("not-registered", STUDENT_PASSWORD),
                Arguments.of(" student ", STUDENT_PASSWORD),
                Arguments.of("student", " " + STUDENT_PASSWORD + " "));
    }

    @Test
    void incompatibleAcceptReturnsEmptyNotAcceptable() {
        given().contentType("application/json").accept("text/plain").body("{}")
                .when().post(LOGIN_PATH)
                .then().statusCode(406).body(emptyString());
    }

    @Test
    void unsupportedContentTypeReturnsEmptyUnsupportedMediaType() {
        given().contentType("text/plain").body("{}")
                .when().post(LOGIN_PATH)
                .then().statusCode(415).body(emptyString());
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() {
        given()
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:4200");

        given()
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .statusCode(403)
                .body(emptyString());
    }

    @Test
    void unexpectedExceptionDoesNotExposeItsDetailsInHttpResponse() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public AuthenticationResult authenticate(String username, String password) {
                throw new IllegalStateException("database failure details");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "irrelevant";

        Response response = resource.login(request);
        assertEquals(500, response.getStatus());
        assertEquals(Map.of("message", "Internal server error", "exception", "InternalServerError"),
                response.getEntity());
        assertTrue(!response.getEntity().toString().contains("database failure details"));
    }

    @Test
    void unexpectedServiceFailureReturnsGenericErrorOverHttp() {
        QuarkusMock.installMockForType(new AuthService() {
            @Override
            public AuthenticationResult authenticate(String username, String password) {
                throw new IllegalStateException("controlled-test-database-failure");
            }
        }, AuthService.class);

        given().contentType("application/json")
                .body(Map.of("username", "student", "password", STUDENT_PASSWORD))
                .when().post(LOGIN_PATH)
                .then().statusCode(500).contentType("application/json")
                .body("size()", is(2))
                .body("message", is("Internal server error"))
                .body("exception", is("InternalServerError"));
    }

    @Test
    void invalidOrAlteredTokenCannotBeVerified() {
        String token = tokenService.create("student", "STUDENT");
        String[] parts = token.split("\\.");
        String modifiedPayload = parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("a") ? "b" : "a");

        assertThrows(IllegalArgumentException.class,
                () -> tokenService.verify(parts[0] + "." + modifiedPayload + "." + parts[2]));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify("not-a-jwt"));
    }
}
