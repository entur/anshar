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

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class LivenessReadinessTest extends BaseHttpTest {

    @Test
    public void testHealthyEndpoint() {
        given()
                .when()
                .get("/healthy")
                .then()
                .statusCode(200);
    }

    @Test
    public void testReadyEndpoint() {
        given()
                .when()
                .get("/ready")
                .then()
                .statusCode(200);
    }

    @Test
    public void testUpEndpoint() {
        given()
                .when()
                .get("/up")
                .then()
                .statusCode(200);
    }

    @Test
    public void testDataEndpoint() {
        given()
                .when()
                .get("/anshardata")
                .then()
                .statusCode(200);
    }
}
