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
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiriSubscriptionTest extends BaseHttpTest {

    @Autowired
    private
    ServerSubscriptionManager subscriptionManager;

    @Test
    public void testCreateSubscription() throws JAXBException {
        SubscriptionSetup subscriptionSetup = getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE);

        Siri subscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);

        assertTrue(subscriptionManager.getSubscriptions().isEmpty());

        //Create Subscription
        given()
            .when()
                .body(SiriXml.toXml(subscriptionRequest))
                .post("anshar/subscribe")
            .then()
                .statusCode(200)
                .body("Siri.SubscriptionResponse.ResponseStatus.Status", equalTo("true"));

        //Verify that it exists
        assertTrue(subscriptionManager.getSubscriptions().size() == 1);
        OutboundSubscriptionSetup outboundSubscription = (OutboundSubscriptionSetup) subscriptionManager.getSubscriptions().iterator().next();
        assertTrue(outboundSubscription.getSubscriptionId().equals(TEST_SUBSCRIPTION_ID));

        Siri terminateSubscriptionRequest = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);
        // Terminate Subscription
        given()
                .when()
                    .body(SiriXml.toXml(terminateSubscriptionRequest))
                    .post("anshar/subscribe")
                .then()
                    .statusCode(200)
                    .body("Siri.TerminateSubscriptionResponse.TerminationResponseStatus.Status", equalTo("true"));

        // Verify that it has been removed
        assertTrue(subscriptionManager.getSubscriptions().isEmpty());
    }

    @Test
    public void testTerminateSubscriptionsByRequestorRef() throws JAXBException, XMLStreamException {
        String subscriptionIdPrefix = "sub-" + System.currentTimeMillis() + "-";
        SubscriptionSetup subscriptionSetup = getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE);
        subscriptionSetup.setSubscriptionId(subscriptionIdPrefix + "1");
        subscriptionSetup.setRequestorRef("test-requestor-1");

        SubscriptionSetup subscriptionSetup2 = getSubscriptionSetup(SiriDataType.VEHICLE_MONITORING);
        subscriptionSetup2.setSubscriptionId(subscriptionIdPrefix + "2");
        subscriptionSetup2.setRequestorRef("test-requestor-1");

        Siri subscriptionRequest_1 = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        Siri subscriptionRequest_2 = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup2);

        assertTrue(subscriptionManager.getSubscriptions().isEmpty());

        //Create Subscriptions
        given()
                .when()
                .body(SiriXml.toXml(subscriptionRequest_1))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.SubscriptionResponse.ResponseStatus.Status", equalTo("true"));
        given()
                .when()
                .body(SiriXml.toXml(subscriptionRequest_2))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.SubscriptionResponse.ResponseStatus.Status", equalTo("true"));


        //Verify that it exists
        assertTrue(subscriptionManager.getSubscriptions().size() == 2);

        for (Object subscription : subscriptionManager.getSubscriptions()) {
            OutboundSubscriptionSetup outboundSubscription = (OutboundSubscriptionSetup) subscription;
            assertTrue(outboundSubscription.getSubscriptionId().startsWith(subscriptionIdPrefix));

        }

        Siri terminateSubscriptionRequest = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

        terminateSubscriptionRequest.getTerminateSubscriptionRequest().getSubscriptionReves().clear();
        terminateSubscriptionRequest.getTerminateSubscriptionRequest().setSubscriberRef(subscriptionRequest_1.getSubscriptionRequest().getRequestorRef());
        terminateSubscriptionRequest.getTerminateSubscriptionRequest().setAll("");

        // Terminate Subscription
        given()
                .when()
                .body(SiriXml.toXml(terminateSubscriptionRequest))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.TerminateSubscriptionResponse.TerminationResponseStatus.Status", equalTo("true"))
                .body("Siri.@version", equalTo("2.1"));

        // Terminate Subscription
        given()
                .when()
                .body(org.rutebanken.siri20.util.SiriXml.toXml(downgradeSiriVersion(terminateSubscriptionRequest)))
                .post("anshar/subscribe")
                .then()
                .statusCode(200)
                .body("Siri.TerminateSubscriptionResponse.TerminationResponseStatus.Status", equalTo("true"))
                .body("Siri.@version", equalTo("2.0"));

        // Verify that it has been removed
        assertTrue(subscriptionManager.getSubscriptions().isEmpty());
    }
}
