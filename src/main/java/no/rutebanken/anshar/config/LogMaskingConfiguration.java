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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class LogMaskingConfiguration {

    /**
     * The custom headers are vendor-specific and may hold anything from a harmless client-name to an api-key,
     * so every configured header name is masked - the key alone tells us the header was applied.
     */
    @Bean
    public MaskedHeadersExchangeFormatter maskedHeadersExchangeFormatter(SubscriptionConfig subscriptionConfig) {
        Set<String> customHeaderNames = new LinkedHashSet<>();

        List<SubscriptionSetup> subscriptions = subscriptionConfig.getSubscriptions();
        if (subscriptions != null) {
            for (SubscriptionSetup subscription : subscriptions) {
                if (subscription.getCustomHeaders() != null) {
                    customHeaderNames.addAll(subscription.getCustomHeaders().keySet());
                }
            }
        }

        return new MaskedHeadersExchangeFormatter(customHeaderNames);
    }
}
