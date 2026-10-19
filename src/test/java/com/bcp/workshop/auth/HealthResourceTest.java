package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.health.SmallRyeHealthReporter;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;

@QuarkusTest
class HealthResourceTest {
    private static final String HEALTH_PATH = "/q/health";
    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String TEST_CHECK_NAME = "test-only-controlled-failure";

    @Inject
    SmallRyeHealthReporter healthReporter;

    @Test
    void publicHealthReturnsUpWithoutAuthentication() {
        given().accept("application/json")
                .when().get(HEALTH_PATH)
                .then().statusCode(200).contentType("application/json")
                .body("size()", is(2))
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void noChecksAndNoContentTypeStillReturnUpForAllowedOrigin() {
        given().header("Origin", ALLOWED_ORIGIN)
                .when().get(HEALTH_PATH)
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void untrustedOriginReturnsEmptyForbidden() {
        given().header("Origin", "https://untrusted.example")
                .when().get(HEALTH_PATH)
                .then().statusCode(403).body(emptyString());
    }

    @Test
    void controlledDownReportReturnsServiceUnavailableAndIsRemoved() {
        HealthCheck failingCheck = () -> HealthCheckResponse.down(TEST_CHECK_NAME);
        healthReporter.addHealthCheck(failingCheck);
        try {
            given().accept("application/json")
                    .when().get(HEALTH_PATH)
                    .then().statusCode(503).contentType("application/json")
                    .body("status", is("DOWN"))
                    .body("checks.size()", is(1))
                    .body("checks[0].name", is(TEST_CHECK_NAME))
                    .body("checks[0].status", is("DOWN"));
        } finally {
            healthReporter.removeHealthCheck(failingCheck);
        }

        given().when().get(HEALTH_PATH)
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks", empty());
    }
}
