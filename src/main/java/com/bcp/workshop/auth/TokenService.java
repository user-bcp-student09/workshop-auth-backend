package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

public class TokenService {
    private static final Logger LOG = Logger.getLogger(TokenService.class);
    public static final int EXPIRY_SECONDS = 3600;
    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Clock clock;
    private final ObjectMapper mapper;
    private final byte[] signingKey;

    public TokenService(String secret, ObjectMapper mapper, Clock clock) {
        this.signingKey = configuredSecret(secret).getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
    }

    static String configuredSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            LOG.error("AUTH_TOKEN_SECRET requires explicit configuration");
            throw new IllegalStateException("AUTH_TOKEN_SECRET requires explicit configuration");
        }
        return secret;
    }

    public IssuedToken issue(String subject, String client) {
        if (subject == null || subject.isBlank() || client == null || client.isBlank()) {
            throw new IllegalArgumentException("Token identity and client are required");
        }
        Claims claims = claimsAtIssuance(subject, client);
        try {
            String signedContent = VERSION + "." + ENCODER.encodeToString(mapper.writeValueAsBytes(claims));
            return new IssuedToken(signedContent + "." + ENCODER.encodeToString(sign(signedContent)), claims);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Token encoding failed");
        }
    }

    public Claims verify(String token) {
        if (token == null) {
            throw invalid("format");
        }
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3 || !VERSION.equals(segments[0])) {
            throw invalid("format");
        }
        byte[] payload = decodeCanonical(segments[1]);
        byte[] signature = decodeCanonical(segments[2]);
        if (!MessageDigest.isEqual(sign(segments[0] + "." + segments[1]), signature)) {
            throw invalid("signature");
        }
        JsonNode claims;
        try {
            claims = mapper.readTree(payload);
        } catch (java.io.IOException error) {
            throw invalid("claims");
        }
        if (claims == null || !claims.isObject() || !textClaim(claims, "sub")
                || !textClaim(claims, "client") || !integerClaim(claims, "iat")
                || !integerClaim(claims, "exp")) {
            throw invalid("claims");
        }
        long issuedAt = claims.get("iat").longValue();
        long expiresAt = claims.get("exp").longValue();
        try {
            if (Math.subtractExact(expiresAt, issuedAt) != EXPIRY_SECONDS) {
                throw invalid("claims");
            }
        } catch (ArithmeticException error) {
            throw invalid("claims");
        }
        if (clock.instant().getEpochSecond() >= expiresAt) {
            throw invalid("expiration");
        }
        return new Claims(claims.get("sub").textValue(), claims.get("client").textValue(),
                issuedAt, expiresAt);
    }

    public void validateForResponse(IssuedToken issued) {
        try {
            long remaining = Math.subtractExact(issued.claims().exp(), clock.instant().getEpochSecond());
            long difference = Math.subtractExact(EXPIRY_SECONDS, remaining);
            if (difference < -1 || difference > 1) {
                throw new IllegalStateException("Token response timing failed");
            }
        } catch (ArithmeticException error) {
            throw new IllegalStateException("Token response timing failed");
        }
    }

    public String safeErrorMessage(String message, String password, String token) {
        String safe = message == null ? "Unknown error" : message;
        for (String sensitive : new String[] {new String(signingKey, StandardCharsets.UTF_8), password, token}) {
            if (sensitive != null && !sensitive.isEmpty()) {
                safe = safe.replace(sensitive, "[redacted]");
            }
        }
        return safe;
    }

    Claims claimsAtIssuance(String subject, String client) {
        long issuedAt = clock.instant().getEpochSecond();
        return new Claims(subject, client, issuedAt, Math.addExact(issuedAt, EXPIRY_SECONDS));
    }

    Mac signingMac() throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(signingKey, ALGORITHM));
        return mac;
    }

    private byte[] sign(String content) {
        try {
            return signingMac().doFinal(content.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException error) {
            LOG.error("Token cryptographic operation failed");
            throw new IllegalStateException("Token cryptographic operation failed");
        }
    }

    private byte[] decodeCanonical(String encoded) {
        try {
            byte[] decoded = DECODER.decode(encoded);
            if (encoded.isEmpty() || !ENCODER.encodeToString(decoded).equals(encoded)) {
                throw invalid("format");
            }
            return decoded;
        } catch (IllegalArgumentException error) {
            throw invalid("format");
        }
    }

    private boolean textClaim(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        return value != null && value.isTextual() && !value.textValue().isBlank();
    }

    private boolean integerClaim(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToLong();
    }

    private InvalidTokenException invalid(String reason) {
        LOG.warnf("Token rejected (%s)", reason);
        return new InvalidTokenException(reason);
    }

    public static class InvalidTokenException extends IllegalArgumentException {
        InvalidTokenException(String reason) {
            super("Invalid token: " + reason);
        }
    }

    public record Claims(String sub, String client, long iat, long exp) {}

    public record IssuedToken(String token, Claims claims) {
        public int expiresIn() {
            return EXPIRY_SECONDS;
        }
    }
}