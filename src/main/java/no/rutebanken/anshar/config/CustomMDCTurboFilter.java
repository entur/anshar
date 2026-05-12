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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;

public class CustomMDCTurboFilter extends TurboFilter {

    String MDCKey = PARAM_SUBSCRIPTION_ID;

    /**
     * SubscriptionIds that should have reduced logging
     */
    private static Set<String> subscriptionIdsWithReducedLogging = new HashSet<>();

    /**
     * Logger-names that should be included even if reduced logging is set
     */
    private static Set<String> loggerOverrideNames = new HashSet<>();

    public static void reduceLogging(String subscriptionId) {
        subscriptionIdsWithReducedLogging.add(subscriptionId);
    }
    public static void loggerIgnoreReducedLogging(List<String> loggerNames) {
        loggerOverrideNames.addAll(loggerNames);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String s, Object[] objects, Throwable throwable) {
        if (this.MDCKey == null) {
            return FilterReply.NEUTRAL;
        } else {
            String value = MDC.get(this.MDCKey);

            if (subscriptionIdsWithReducedLogging.contains(value)) {

                if (logger != null && loggerOverrideNames.contains(logger.getName())) {
                    return FilterReply.NEUTRAL;
                }

                // If reduced logging is set - DO NOT accept logging below WARN
                if (level.isGreaterOrEqual(Level.WARN)) {
                    return FilterReply.ACCEPT;
                } else {
                    return FilterReply.DENY;
                }
            }
            return FilterReply.NEUTRAL;
        }
    }
}
