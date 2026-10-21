package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.util.Optional;

@Startup
@ApplicationScoped
public class TokenConfiguration {
    private final String secret;
    private final ObjectMapper mapper;

    @Inject
    public TokenConfiguration(@ConfigProperty(name = "auth.token.secret") Optional<String> secret,
                              ObjectMapper mapper) {
        this.secret = TokenService.configuredSecret(secret.orElse(null));
        this.mapper = mapper;
    }

    @Produces
    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    public TokenService tokens(Clock clock) {
        return new TokenService(secret, mapper, clock);
    }
}