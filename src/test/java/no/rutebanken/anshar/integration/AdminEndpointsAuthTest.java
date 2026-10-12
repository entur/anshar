/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.integration;

import no.rutebanken.anshar.routes.admin.auth.BasicAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static io.restassured.RestAssured.given;

public class AdminEndpointsAuthTest extends BaseHttpTest {

    @Autowired
    private BasicAuthService basicAuthService;

    @Test
    public void testLocksRequiresAuthentication() {
        given()
                .when()
                .get("/locks")
                .then()
                .statusCode(401);
    }

    @Test
    public void testLocksAcceptsValidCredentials() {
        Object username = ReflectionTestUtils.getField(basicAuthService, "username");
        Object password = ReflectionTestUtils.getField(basicAuthService, "password");
        ReflectionTestUtils.setField(basicAuthService, "username", "admin");
        ReflectionTestUtils.setField(basicAuthService, "password", "secret");
        try {
            given()
                    .auth().preemptive().basic("admin", "secret")
                    .when()
                    .get("/locks")
                    .then()
                    .statusCode(200);

            given()
                    .auth().preemptive().basic("admin", "wrong")
                    .when()
                    .get("/locks")
                    .then()
                    .statusCode(401);
        } finally {
            ReflectionTestUtils.setField(basicAuthService, "username", username);
            ReflectionTestUtils.setField(basicAuthService, "password", password);
        }
    }

    @Test
    public void testFlushDatasetRequiresAuthentication() {
        given()
                .when()
                .delete("/flush-dataset")
                .then()
                .statusCode(401);
    }

    @Test
    public void testClearUnmappedRequiresAuthentication() {
        given()
                .when()
                .delete("/unmapped/" + dataSource)
                .then()
                .statusCode(401);
    }

    @Test
    public void testClusterstatsRequiresAuthentication() {
        given()
                .when()
                .get("/anshar/clusterstats")
                .then()
                .statusCode(401);
    }

    @Test
    public void testUnmappedIsOpen() {
        given()
                .when()
                .get("/anshar/unmapped/" + dataSource)
                .then()
                .statusCode(200);
    }

    @Test
    public void testPrepareShutdownRejectsForwardedRequests() {
        // Requests routed through ingress/proxy carry X-Forwarded-For - only the local preStop-hook is allowed
        given()
                .header("X-Forwarded-For", "203.0.113.10")
                .when()
                .get("/prepare-shutdown")
                .then()
                .statusCode(401);
    }
}
