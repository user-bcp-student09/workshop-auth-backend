package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenConfigurationTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void missingEmptyAndBlankSecretsFailWithoutFallback(String secret) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new TokenConfiguration(Optional.ofNullable(secret), new ObjectMapper()));
        assertEquals("AUTH_TOKEN_SECRET requires explicit configuration", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void validUtf8SecretIsNotTrimmedAndNoExtraMinimumIsImposed() {
        ObjectMapper mapper = new ObjectMapper();
        String secret = " Fictional-Key-\u00e9 ";
        TokenConfiguration configuration = new TokenConfiguration(Optional.of(secret), mapper);
        TokenService issuer = configuration.tokens(configuration.clock());
        String token = issuer.issue("student", "app").token();
        assertEquals("app", new TokenService(secret, mapper, Clock.systemUTC()).verify(token).client());
        assertThrows(TokenService.InvalidTokenException.class,
                () -> new TokenService(secret.trim(), mapper, Clock.systemUTC()).verify(token));
        TokenConfiguration shortKey = new TokenConfiguration(Optional.of("x"), mapper);
        assertEquals(3600, shortKey.tokens(shortKey.clock()).issue("student", "app").expiresIn());
    }
}