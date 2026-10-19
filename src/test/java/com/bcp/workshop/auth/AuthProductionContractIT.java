package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.nullValue;

@QuarkusIntegrationTest
@TestProfile(AuthProductionContractIT.IsolatedConfiguration.class)
class AuthProductionContractIT {
    @Test
    void incompatibleDtoReturnsEmptyBadRequestInProductionMode() {
        given().contentType("application/json")
                .body("{\"username\":{},\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then().statusCode(400)
                .header("Content-Type", nullValue())
                .body(emptyString());
    }

    public static class IsolatedConfiguration implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "auth.database.url", "jdbc:sqlite:./target/auth-production-contract-test.db",
                    "auth.student.password", "FictionalStudentPassword-2026",
                    "auth.trainer.password", "FictionalTrainerPassword-2026",
                    "auth.jwt.secret", "Fictional-Test-Only-Signing-Key-32-Bytes-Minimum",
                    "quarkus.http.cors.origins", "http://localhost:4200",
                    "quarkus.http.host", "127.0.0.1");
        }
    }
}
