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

import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiriSubscriptionTest extends BaseHttpTest {

    @Autowired
    private
    ServerSubscriptionManager subscriptionManager;

    @Test
    @Disabled
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
}
