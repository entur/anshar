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

package no.rutebanken.anshar.config;

import no.rutebanken.anshar.routes.siri.helpers.MaskedHeadersExchangeFormatter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogMaskingConfigurationTest {

    private static final String SECRET = "sEcReT-vAlUe";

    private final LogMaskingConfiguration configuration = new LogMaskingConfiguration();
    private final CamelContext context = new DefaultCamelContext();

    @Test
    public void masksCustomHeadersConfiguredOnAnySubscription() {
        MaskedHeadersExchangeFormatter formatter = configuration.maskedHeadersExchangeFormatter(
                subscriptionConfig(
                        subscriptionWithCustomHeaders(Map.of("ET-Client-Name", "anshar")),
                        subscriptionWithCustomHeaders(Map.of("Ocp-Apim-Subscription-Key", SECRET))));

        assertFalse(formatted(formatter, "ET-Client-Name").contains(SECRET));
        assertFalse(formatted(formatter, "Ocp-Apim-Subscription-Key").contains(SECRET));
    }

    @Test
    public void masksCredentialHeadersWhenNoSubscriptionsAreConfigured() {
        MaskedHeadersExchangeFormatter formatter =
                configuration.maskedHeadersExchangeFormatter(new SubscriptionConfig());

        assertFalse(formatted(formatter, "Authorization").contains(SECRET));
    }

    @Test
    public void toleratesSubscriptionsWithoutCustomHeaders() {
        MaskedHeadersExchangeFormatter formatter = configuration.maskedHeadersExchangeFormatter(
                subscriptionConfig(new SubscriptionSetup()));

        assertTrue(formatted(formatter, "CamelHttpMethod").contains(SECRET),
                "unrelated headers should still be logged");
    }

    private String formatted(MaskedHeadersExchangeFormatter formatter, String headerName) {
        Exchange e = new DefaultExchange(context);
        e.getMessage().setBody("<Siri/>");
        e.getMessage().setHeader(headerName, SECRET);
        return formatter.format(e);
    }

    private SubscriptionConfig subscriptionConfig(SubscriptionSetup... subscriptions) {
        SubscriptionConfig config = new SubscriptionConfig();
        config.setSubscriptions(List.of(subscriptions));
        return config;
    }

    private SubscriptionSetup subscriptionWithCustomHeaders(Map<String, Object> customHeaders) {
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setCustomHeaders(customHeaders);
        return setup;
    }
}
