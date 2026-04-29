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

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;

public abstract class BaseHttpTest extends SpringBootBaseTest {

    static final String TEST_SUBSCRIPTION_ID = "test.subscription.id";
    @Value("${anshar.incoming.port}")
    private int port;

    static final String dataSource = "TTT";

    @BeforeEach
    public void init() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.filters(new RequestLoggingFilter());
        RestAssured.filters(new ResponseLoggingFilter());
    }

    SubscriptionSetup getSubscriptionSetup(SiriDataType subscriptionType) {
        SubscriptionSetup sub = new SubscriptionSetup();
        sub.setSubscriptionType(subscriptionType);
        sub.setRequestorRef("TestSubscription");
        sub.setSubscriptionId(TEST_SUBSCRIPTION_ID);
        sub.setDurationOfSubscriptionHours(1);
        sub.setAddress("http://localhost:1234/incoming");
        return sub;
    }
}
