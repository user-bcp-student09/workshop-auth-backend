package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.text.StringEscapeUtils;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthService service;

    @Inject
    TokenService tokens;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        AuthService.AuthenticationResult authenticated = null;
        try {
            if (request == null) {
                return Response.status(400).entity(Map.of("message", "Request is null")).build();
            }
            if (request.username == null || request.username.trim().isEmpty()) {
                return Response.status(400).entity(Map.of("message", "Username is required")).build();
            }
            if (request.password == null || request.password.trim().isEmpty()) {
                return Response.status(400).entity(Map.of("message", "Password is required")).build();
            }
            if (request.client == null || !request.client.isTextual() || request.client.textValue().isBlank()) {
                return Response.status(400).entity(Map.of("message", "Client is required")).build();
            }
            authenticated = service.authenticate(
                    request.username, request.password, request.client.textValue());
            if (authenticated == null) {
                return Response.status(401).entity(Map.of("message", "Invalid user " + request.username + " or password")).build();
            }
            UserRecord user = authenticated.user();
            Map<String, Object> response = new HashMap<>();
            response.put("token", authenticated.token());
            response.put("expiresIn", authenticated.expiresIn());
            response.put("user", Map.of(
                    "username", user.username(),
                    "displayName", StringEscapeUtils.escapeHtml4(user.displayName()),
                    "role", user.role()
            ));
            response.put("debug", "authenticated-with-legacy-flow");
            service.validateForResponse(authenticated);
            LOG.info("Authentication completed");
            return Response.ok(response).build();
        } catch (Exception error) {
            LOG.error("Authentication failed");
            return Response.status(500).entity(Map.of(
                "message", tokens.safeErrorMessage(error.getMessage(), request == null ? null : request.password,
                    authenticated == null ? null : authenticated.token()),
                    "exception", error.getClass().getName()
            )).build();
        }
    }
}
