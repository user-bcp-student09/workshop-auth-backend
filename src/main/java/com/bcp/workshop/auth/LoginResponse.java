package com.bcp.workshop.auth;

public record LoginResponse(String token, int expiresIn, UserDto user, String debug) {
    public record UserDto(String username, String displayName, String role) {}
}
