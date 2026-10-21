package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;

public class LoginRequest {
    public String username;
    public String password;
    public JsonNode client;
}
