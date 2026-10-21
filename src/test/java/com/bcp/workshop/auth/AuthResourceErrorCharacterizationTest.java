package com.bcp.workshop.auth;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthResourceErrorCharacterizationTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "Test failure")
    void unexpectedExceptionsPreserveLegacyErrorContract(String message) {
        AuthResource resource = new AuthResource();
        resource.tokens = new TokenService(TokenServiceTest.TEST_SECRET,
            new com.fasterxml.jackson.databind.ObjectMapper(), java.time.Clock.systemUTC());
        resource.service = new AuthService();
        resource.service.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new RuntimeException(message);
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = AuthCharacterizationResource.STUDENT_PASSWORD;
        request.client = com.fasterxml.jackson.databind.node.TextNode.valueOf("angular-workshop");

        try (Response response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            Map<?, ?> entity = assertInstanceOf(Map.class, response.getEntity());
            assertEquals(Map.of("message", message == null ? "Unknown error" : message,
                    "exception", "java.lang.RuntimeException"), entity);
            assertFalse(entity.containsKey("token"));
        }
    }
}