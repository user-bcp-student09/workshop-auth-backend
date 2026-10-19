package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {
    private static final String TEST_SIGNING_KEY = "Fictional-Test-Only-Signing-Key-32-Bytes-Minimum";

    @Test
    void signedTokenContainsExplicitClaimsAndExpires() {
        Instant issuedAt = Instant.parse("2026-10-19T12:00:00Z");
        Clock clock = Clock.fixed(issuedAt, ZoneOffset.UTC);
        TokenService tokenService = new TokenService(TEST_SIGNING_KEY, new ObjectMapper(), clock);

        String token = tokenService.create("student", "STUDENT");
        TokenService.TokenClaims claims = tokenService.verify(token);

        assertEquals("student", claims.subject());
        assertEquals("STUDENT", claims.role());
        assertEquals(issuedAt.plusSeconds(TokenService.TOKEN_EXPIRY_SECONDS).getEpochSecond(), claims.expiresAt());

        TokenService expiredTokenService = new TokenService(
                TEST_SIGNING_KEY,
                new ObjectMapper(),
                Clock.fixed(issuedAt.plusSeconds(TokenService.TOKEN_EXPIRY_SECONDS), ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> expiredTokenService.verify(token));
    }

    @Test
    void tokenIsValidOneSecondBeforeExpirationButNotAtOrAfterExpiration() {
        Instant issuedAt = Instant.parse("2026-10-19T12:00:00Z");
        ObjectMapper objectMapper = new ObjectMapper();
        TokenService issuer = new TokenService(
                TEST_SIGNING_KEY, objectMapper, Clock.fixed(issuedAt, ZoneOffset.UTC));
        String token = issuer.create("student", "STUDENT");
        Instant expiration = issuedAt.plusSeconds(TokenService.TOKEN_EXPIRY_SECONDS);
        TokenService beforeExpiration = new TokenService(
                TEST_SIGNING_KEY, objectMapper, Clock.fixed(expiration.minusSeconds(1), ZoneOffset.UTC));
        assertEquals("student", beforeExpiration.verify(token).subject());

        for (Instant rejectedInstant : new Instant[] {expiration, expiration.plusSeconds(1)}) {
            TokenService verifier = new TokenService(
                    TEST_SIGNING_KEY, objectMapper, Clock.fixed(rejectedInstant, ZoneOffset.UTC));
            assertThrows(IllegalArgumentException.class, () -> verifier.verify(token));
        }
    }

    @Test
    void shortSigningSecretIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new TokenService("too-short", new ObjectMapper(), Clock.systemUTC()));
    }

    @Test
    void verifierRejectsMalformedTokensAndMissingClaims() throws Exception {
        TokenService tokenService = new TokenService(TEST_SIGNING_KEY, new ObjectMapper(), Clock.systemUTC());
        Map<String, Object> validClaims = Map.of(
                "sub", "student",
                "role", "STUDENT",
                "exp", Instant.now().plusSeconds(600).getEpochSecond());
        Map<String, Object> validHeader = Map.of("alg", "HS256", "typ", "JWT");

        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(null));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify("not-a-jwt"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify("!.e30.signature"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(
                signedToken(Map.of("alg", "HS512", "typ", "JWT"), validClaims)));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(
                signedToken(validHeader, Map.of("role", "STUDENT", "exp", Instant.now().plusSeconds(600).getEpochSecond()))));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(
                signedToken(validHeader, Map.of("sub", "student", "role", "STUDENT"))));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(
                signedToken(validHeader, Map.of("sub", "student", "role", "STUDENT", "exp", "future"))));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(signedToken(validHeader, "{")));
    }

    @Test
    void creationRejectsMissingIdentityClaims() {
        TokenService tokenService = new TokenService(TEST_SIGNING_KEY, new ObjectMapper(), Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> tokenService.create(null, "STUDENT"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.create("student", null));
    }

    private String signedToken(Object header, Object claims) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(header));
        String encodedClaims = claims instanceof String rawClaims
                ? Base64.getUrlEncoder().withoutPadding().encodeToString(rawClaims.getBytes(StandardCharsets.UTF_8))
                : Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(claims));
        String signingInput = encodedHeader + "." + encodedClaims;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + signature;
    }
}
