package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class TokenService {
    public static final int TOKEN_EXPIRY_SECONDS = 3600;
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] signingKey;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.jwt.secret") String secret, ObjectMapper objectMapper) {
        this(secret, objectMapper, Clock.systemUTC());
    }

    TokenService(String secret, ObjectMapper objectMapper, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException("auth.jwt.secret must contain at least 32 bytes");
        }
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String create(String username, String role) {
        if (username == null || role == null) {
            throw new IllegalArgumentException("Token subject and role are required");
        }

        Instant issuedAt = Instant.now(clock);
        Map<String, Object> header = Map.of("alg", JWT_ALGORITHM, "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "sub", username,
                "role", role,
                "iat", issuedAt.getEpochSecond(),
                "exp", issuedAt.plusSeconds(TOKEN_EXPIRY_SECONDS).getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedClaims = encodeJson(claims);
        String signingInput = encodedHeader + "." + encodedClaims;
        return signingInput + "." + URL_ENCODER.encodeToString(sign(signingInput));
    }

    public TokenClaims verify(String token) {
        if (token == null) {
            throw invalidToken();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw invalidToken();
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] providedSignature;
        byte[] header;
        byte[] payload;
        try {
            providedSignature = URL_DECODER.decode(parts[2]);
            header = URL_DECODER.decode(parts[0]);
            payload = URL_DECODER.decode(parts[1]);
        } catch (IllegalArgumentException error) {
            throw invalidToken();
        }
        if (!java.security.MessageDigest.isEqual(sign(signingInput), providedSignature)) {
            throw invalidToken();
        }

        try {
            validateHeader(header);
            JsonNode claims = objectMapper.readTree(payload);
            String subject = textClaim(claims, "sub");
            String role = textClaim(claims, "role");
            JsonNode expiration = claims.get("exp");
            if (expiration == null || !expiration.isIntegralNumber()
                    || Instant.now(clock).getEpochSecond() >= expiration.longValue()) {
                throw invalidToken();
            }
            return new TokenClaims(subject, role, expiration.longValue());
        } catch (IOException error) {
            throw invalidToken();
        }
    }

    private void validateHeader(byte[] encodedHeader) throws IOException {
        JsonNode header = objectMapper.readTree(encodedHeader);
        if (header == null || !JWT_ALGORITHM.equals(textClaim(header, "alg"))
                || !"JWT".equals(textClaim(header, "typ"))) {
            throw invalidToken();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to encode JWT", error);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, SIGNING_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Unable to sign JWT", error);
        }
    }

    private String textClaim(JsonNode claims, String name) {
        JsonNode claim = claims.get(name);
        if (claim == null || !claim.isTextual() || claim.textValue().isBlank()) {
            throw invalidToken();
        }
        return claim.textValue();
    }

    private IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("Invalid or expired token");
    }

    public record TokenClaims(String subject, String role, long expiresAt) {}
}
