package io.quarkiverse.helm.tests.kubernetes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Quarkus Tests should be compatible with the Helm extension.
 */
@QuarkusTest
public class EndpointTest {
    @Test
    public void testEndpoint() {
        given()
                .when().get()
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(is("Hello, World!"));
    }
}
