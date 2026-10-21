package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-10-21T12:00:00Z");
    static final String TEST_SECRET = "Fictional-Only-Token-Key-2026-32-Bytes";
    private final ObjectMapper mapper = new ObjectMapper();

    private TokenService service(Clock clock) {
        return new TokenService(TEST_SECRET, mapper, clock);
    }

    @Test
    void claimsAndMetadataShareOneHourValidityAndClock() {
        TokenService service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        TokenService.Claims claims = service.claimsAtIssuance("student", " angular-workshop ");
        TokenService.IssuedToken issued = new TokenService.IssuedToken("test-only-placeholder", claims);
        assertEquals("student", claims.sub());
        assertEquals(" angular-workshop ", claims.client());
        assertEquals(ISSUED_AT.getEpochSecond(), claims.iat());
        assertEquals(ISSUED_AT.getEpochSecond() + 3600, claims.exp());
        assertEquals(3600, issued.expiresIn());
    }

    @Test
    void signedEnvelopeHasIndependentSignatureAndExactClaimsWithoutSecrets() throws Exception {
        TokenService service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        TokenService.IssuedToken issued = service.issue("student", " angular-workshop ");
        String[] segments = issued.token().split("\\.");
        assertEquals(3, segments.length);
        assertEquals("v1", segments[0]);
        assertFalse(issued.token().contains("="));
        byte[] signature = independentSignature(segments[0] + "." + segments[1]);
        assertTrue(MessageDigest.isEqual(signature, Base64.getUrlDecoder().decode(segments[2])));
        String payload = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        ObjectNode claims = (ObjectNode) mapper.readTree(payload);
        assertEquals(Set.of("sub", "client", "iat", "exp"),
                mapper.convertValue(claims, java.util.Map.class).keySet());
        assertEquals("student", claims.get("sub").textValue());
        assertEquals(" angular-workshop ", claims.get("client").textValue());
        assertTrue(claims.get("iat").isIntegralNumber());
        assertEquals(ISSUED_AT.getEpochSecond(), claims.get("iat").longValue());
        assertTrue(claims.get("exp").isIntegralNumber());
        assertEquals(3600, claims.get("exp").longValue() - claims.get("iat").longValue());
        assertFalse(payload.contains(TEST_SECRET));
        assertFalse(payload.contains(AuthCharacterizationResource.STUDENT_PASSWORD));
        assertFalse(issued.token().contains(TEST_SECRET));
        assertEquals(issued.claims(), service.verify(issued.token()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "client", "iat", "exp"})
    void modifyingAnyClaimWithoutResigningIsRejected(String field) throws Exception {
        TokenService service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String[] segments = service.issue("student", "angular-workshop").token().split("\\.");
        ObjectNode payload = (ObjectNode) mapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
        if (field.equals("sub") || field.equals("client")) {
            payload.put(field, "changed");
        } else {
            payload.put(field, payload.get(field).longValue() + 1);
        }
        String altered = "v1." + encode(mapper.writeValueAsBytes(payload)) + "." + segments[2];
        assertThrows(TokenService.InvalidTokenException.class, () -> service.verify(altered));
    }

    @Test
    void alteredSignatureIsRejected() {
        TokenService service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String[] segments = service.issue("student", "angular-workshop").token().split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(segments[2]);
        signature[0] ^= 1;
        String altered = segments[0] + "." + segments[1] + "." + encode(signature);
        assertThrows(TokenService.InvalidTokenException.class, () -> service.verify(altered));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "legacy-base64", "v2.e30.AA", "v1..AA", "v1.e30.",
            "v1.e30=.AA", "v1.e30.AA=", "v1.!.AA", "v1.e30.AB", "v1.e30.AA.extra"})
    void malformedUnknownOrNoncanonicalEnvelopeIsRejected(String token) {
        TokenService service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        assertThrows(TokenService.InvalidTokenException.class, () -> service.verify(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "client", "iat", "exp"})
    void missingClaimIsRejectedEvenWithCorrectSignature(String field) throws Exception {
        ObjectNode payload = validPayload();
        payload.remove(field);
        String token = signedPayload(mapper.writeValueAsString(payload));
        assertThrows(TokenService.InvalidTokenException.class,
                () -> service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).verify(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "1", "1.5", "\"\"", "\"   \"", "{}", "[]"})
    void subjectAndClientRequireNonblankStrings(String value) throws Exception {
        for (String field : new String[] {"sub", "client"}) {
            ObjectNode payload = validPayload();
            payload.set(field, mapper.readTree(value));
            String token = signedPayload(mapper.writeValueAsString(payload));
            assertThrows(TokenService.InvalidTokenException.class,
                    () -> service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).verify(token));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "\"1792584000\"", "1.5", "{}", "[]", "9223372036854775808"})
    void timeClaimsRequireStrictLongIntegers(String value) throws Exception {
        for (String field : new String[] {"iat", "exp"}) {
            ObjectNode payload = validPayload();
            payload.set(field, mapper.readTree(value));
            String token = signedPayload(mapper.writeValueAsString(payload));
            assertThrows(TokenService.InvalidTokenException.class,
                    () -> service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).verify(token));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{", "{}{}",
            "{\"sub\":\"student\",\"sub\":\"other\",\"client\":\"app\",\"iat\":1,\"exp\":3601}"})
    void signedInvalidJsonAndDuplicateClaimsAreRejected(String payload) throws Exception {
        String token = signedPayload(payload);
        assertThrows(TokenService.InvalidTokenException.class,
                () -> service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).verify(token));
    }

    @Test
    void invalidDurationAndOverflowAreRejected() throws Exception {
        ObjectNode payload = validPayload();
        payload.put("exp", ISSUED_AT.getEpochSecond() + 3599);
        String shortToken = signedPayload(mapper.writeValueAsString(payload));
        assertThrows(TokenService.InvalidTokenException.class,
                () -> service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).verify(shortToken));
        payload.put("iat", Long.MIN_VALUE).put("exp", Long.MAX_VALUE);
        String overflow = signedPayload(mapper.writeValueAsString(payload));
        assertThrows(TokenService.InvalidTokenException.class,
                () -> service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).verify(overflow));
    }

    @Test
    void expirationHasNoGraceAtOrAfterTheBoundary() {
        TokenService.IssuedToken issued = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC))
                .issue("student", "angular-workshop");
        Instant expiration = Instant.ofEpochSecond(issued.claims().exp());
        assertEquals(issued.claims(), service(Clock.fixed(expiration.minusSeconds(1), ZoneOffset.UTC))
                .verify(issued.token()));
        for (long offset : new long[] {0, 1}) {
            TokenService verifier = service(Clock.fixed(expiration.plusSeconds(offset), ZoneOffset.UTC));
            assertThrows(TokenService.InvalidTokenException.class, () -> verifier.verify(issued.token()));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1, -2, 2})
    void responseTimingAcceptsOnlyOneSecondIncludingClockJumps(long offset) {
        AdjustableClock clock = new AdjustableClock();
        TokenService service = service(clock);
        TokenService.IssuedToken issued = service.issue("student", "angular-workshop");
        clock.now = ISSUED_AT.plusSeconds(offset);
        if (offset >= -1 && offset <= 1) {
            service.validateForResponse(issued);
        } else {
            assertThrows(IllegalStateException.class, () -> service.validateForResponse(issued));
        }
        assertEquals(3600, issued.expiresIn());
    }

    @Test
    void responseTimingRejectsOverflow() {
        TokenService service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        TokenService.IssuedToken issued = new TokenService.IssuedToken("unused",
                new TokenService.Claims("student", "app", 0, Long.MIN_VALUE));
        assertThrows(IllegalStateException.class, () -> service.validateForResponse(issued));
    }

    @Test
    void providerFailuresRemainOperationalAndDoNotDiscloseTheirCause() {
        TokenService broken = new TokenService(TEST_SECRET, mapper, Clock.fixed(ISSUED_AT, ZoneOffset.UTC)) {
            @Override
            Mac signingMac() throws GeneralSecurityException {
                throw new GeneralSecurityException("Fictional-sensitive-provider-detail");
            }
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> broken.issue("student", "app"));
        assertEquals("Token cryptographic operation failed", failure.getMessage());
        assertEquals(null, failure.getCause());
        String token = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).issue("student", "app").token();
        assertThrows(IllegalStateException.class, () -> broken.verify(token));
    }

    private ObjectNode validPayload() {
        return mapper.createObjectNode().put("sub", "student").put("client", "angular-workshop")
                .put("iat", ISSUED_AT.getEpochSecond()).put("exp", ISSUED_AT.getEpochSecond() + 3600);
    }

    private String signedPayload(String payload) throws Exception {
        String content = "v1." + encode(payload.getBytes(StandardCharsets.UTF_8));
        return content + "." + encode(independentSignature(content));
    }

    private byte[] independentSignature(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static class AdjustableClock extends Clock {
        Instant now = ISSUED_AT;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}