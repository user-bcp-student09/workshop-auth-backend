package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuthService {
    private static final Logger LOG = Logger.getLogger(AuthService.class);
    @Inject
    LegacyDatabase database;

    @Inject
    TokenService tokens;

    public AuthenticationResult authenticate(String username, String password, String client) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            LOG.info("Authentication rejected");
            return null;
        }
        return new AuthenticationResult(user, tokens.issue(user.username(), client));
    }

    public void validateForResponse(AuthenticationResult authenticated) {
        tokens.validateForResponse(authenticated.issued());
    }

    public record AuthenticationResult(UserRecord user, TokenService.IssuedToken issued) {
        public String token() {
            return issued.token();
        }

        public int expiresIn() {
            return issued.expiresIn();
        }
    }
}