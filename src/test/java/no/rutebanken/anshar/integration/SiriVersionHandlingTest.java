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

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;

import static io.restassured.RestAssured.given;
import static no.rutebanken.anshar.routes.RestRouteBuilder.downgradeSiriVersion;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiriVersionHandlingTest extends BaseHttpTest {

    @Autowired
    private
    ServerSubscriptionManager subscriptionManager;

    @Test
    public void testCreateSubscription() throws JAXBException, XMLStreamException {
        SubscriptionSetup subscriptionSetup = getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE);

        Siri subscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);

        //Create 2.0 Subscription
        given()
                .when()
                .body(org.rutebanken.siri20.util.SiriXml.toXml(downgradeSiriVersion(subscriptionRequest)))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.@version", equalTo("2.0"))
                .body("Siri.SubscriptionResponse.ResponseStatus.Status", equalTo("true"))
        ;
        // Verify that it has been added
        assertFalse(subscriptionManager.getSubscriptions().isEmpty());

        Siri terminateSubscriptionRequest = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

        // Terminate 2.0 Subscription
        given()
                .when()
                .body(org.rutebanken.siri20.util.SiriXml.toXml(downgradeSiriVersion(terminateSubscriptionRequest)))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.@version", equalTo("2.0"))
                .body("Siri.TerminateSubscriptionResponse.TerminationResponseStatus.Status", equalTo("true"))
        ;
        assertTrue(subscriptionManager.getSubscriptions().isEmpty());

        //Create 2.1 Subscription
        given()
                .when()
                .body(SiriXml.toXml(subscriptionRequest))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.@version", equalTo("2.1"))
                .body("Siri.SubscriptionResponse.ResponseStatus.Status", equalTo("true"))
        ;

        // Verify that it has been added
        assertFalse(subscriptionManager.getSubscriptions().isEmpty());

        // Terminate 2.1 Subscription
        given()
                .when()
                    .body(SiriXml.toXml(terminateSubscriptionRequest))
                    .post("anshar/subscribe")
                .then()
                    .statusCode(200)
                    .body("Siri.@version", equalTo("2.1"))
                    .body("Siri.TerminateSubscriptionResponse.TerminationResponseStatus.Status", equalTo("true"))
        ;

        // Verify that it has been removed
        assertTrue(subscriptionManager.getSubscriptions().isEmpty());
    }

    @Test
    public void testCheckStatusRequest() throws JAXBException, XMLStreamException {
        SubscriptionSetup subscriptionSetup = getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE);

        Siri checkStatusRequest = SiriObjectFactory.createCheckStatusRequest(subscriptionSetup);

        //SIRI 2.0 CheckStatusRequest
        given()
                .when()
                .body(org.rutebanken.siri20.util.SiriXml.toXml(downgradeSiriVersion(checkStatusRequest)))
                .post("anshar/services")
                .then()
                .statusCode(200)
                .body("Siri.@version", equalTo("2.0"))
                .body("Siri.CheckStatusResponse.Status", equalTo("true"))
        ;

        //SIRI 2.1 CheckStatusRequest
        given()
                .when()
                .body(SiriXml.toXml(checkStatusRequest))
                .post("anshar/services")
                .then()
                .statusCode(200)
                .body("Siri.@version", equalTo("2.1"))
                .body("Siri.CheckStatusResponse.Status", equalTo("true"))
        ;
    }

}
