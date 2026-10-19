package com.bcp.workshop.auth;

public record AuthenticationResult(UserRecord user, String token, int expiresIn) {}
