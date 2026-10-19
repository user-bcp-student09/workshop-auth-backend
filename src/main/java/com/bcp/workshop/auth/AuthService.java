package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    TokenService tokenService;

    public AuthenticationResult authenticate(String username, String password) {
        UserRecord user = userRepository.findByUsername(username);
        if (user == null || !passwordHasher.verify(password, user.passwordHash())) {
            return null;
        }

        return new AuthenticationResult(
                user,
                tokenService.create(user.username(), user.role()),
                TokenService.TOKEN_EXPIRY_SECONDS);
    }
}
