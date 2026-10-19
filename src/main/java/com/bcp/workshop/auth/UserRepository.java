package com.bcp.workshop.auth;

public interface UserRepository {
    UserRecord findByUsername(String username);
}
