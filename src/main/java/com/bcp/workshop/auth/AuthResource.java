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

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class);
    private static final String GENERIC_ERROR_MESSAGE = "Internal server error";
    private static final String GENERIC_EXCEPTION_NAME = "InternalServerError";

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return badRequest("Request is null");
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return badRequest("Username is required");
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return badRequest("Password is required");
        }

        try {
            AuthenticationResult result = authService.authenticate(request.username, request.password);
            if (result == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("message", "Invalid user " + request.username + " or password"))
                        .build();
            }

            UserRecord user = result.user();
            LoginResponse response = new LoginResponse(
                    result.token(),
                    result.expiresIn(),
                    new LoginResponse.UserDto(
                            user.username(),
                            StringEscapeUtils.escapeHtml4(user.displayName()),
                            user.role()),
                    "authenticated-with-legacy-flow");
            return Response.ok(response).build();
        } catch (RuntimeException error) {
            LOGGER.error("Unexpected error while processing login", error);
            return Response.serverError().entity(Map.of(
                    "message", GENERIC_ERROR_MESSAGE,
                    "exception", GENERIC_EXCEPTION_NAME)).build();
        }
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", message)).build();
    }
}
