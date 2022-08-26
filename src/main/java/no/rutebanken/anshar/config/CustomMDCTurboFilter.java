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
