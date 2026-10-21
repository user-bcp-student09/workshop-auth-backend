package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    @Test
    void invalidCredentialsCannotBecomeSuccessfulAuthentication() {
        AuthService service = serviceReturning(null);
        assertNull(service.authenticate("student", "wrong", "app"));
    }

    @Test
    void persistenceExceptionsAreNotConvertedToAuthenticationSuccess() {
        AuthService service = new AuthService();
        service.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("Controlled persistence failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> service.authenticate("student", "fictional", "app"));
    }

    @Test
    void persistedIdentityAndRoleArePreservedInTypedResult() {
        UserRecord user = new UserRecord(1, "persisted-student", "Workshop Student", "STUDENT");
        AuthService service = serviceReturning(user);
        AuthService.AuthenticationResult result = service.authenticate("input", "fictional", "app");
        assertEquals(user, result.user());
        assertEquals(3600, result.expiresIn());
        assertNotNull(result.token());
        assertEquals("persisted-student", service.tokens.verify(result.token()).sub());
        assertEquals("app", service.tokens.verify(result.token()).client());
    }

    private AuthService serviceReturning(UserRecord user) {
        AuthService service = new AuthService();
        service.tokens = new TokenService(TokenServiceTest.TEST_SECRET, new ObjectMapper(), Clock.systemUTC());
        service.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                return user;
            }
        };
        return service;
    }
}