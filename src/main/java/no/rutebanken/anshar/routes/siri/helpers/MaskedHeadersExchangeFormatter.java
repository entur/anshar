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

package no.rutebanken.anshar.routes.siri.helpers;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.support.processor.DefaultExchangeFormatter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Logs the outgoing request the same way {@code showAll=true&multiline=true} does, except that the value of
 * every credential-carrying header is replaced by {@link #MASK}. The key is still logged - it is useful to
 * see that a header/oauth-value is configured and applied, the value itself never belongs in the log.
 */
public class MaskedHeadersExchangeFormatter extends DefaultExchangeFormatter {

    public static final String MASK = "<set>";

    private static final String LOG_OPTIONS = "?showAll=true&multiline=true";

    /**
     * Set by {@code direct:oauth2.authorize} and by the vendor-specific custom headers.
     */
    private static final Set<String> ALWAYS_MASKED = Set.of("authorization", "proxy-authorization");

    /**
     * Prefix of the headers holding the oauth2-config while the token is being requested.
     */
    private static final String OAUTH_HEADER_PREFIX = "oauth-";

    private final Set<String> maskedKeys;

    public MaskedHeadersExchangeFormatter(Collection<String> configuredHeaderNames) {
        maskedKeys = configuredHeaderNames.stream()
                .map(MaskedHeadersExchangeFormatter::normalize)
                .collect(Collectors.toUnmodifiableSet());

        setShowAll(true);
        setMultiline(true);
    }

    /**
     * Creates the log-endpoint to use instead of {@code log:<loggerName>?showAll=true&multiline=true} whenever
     * the exchange may carry credentials. The log-component has no uri-option for supplying a formatter, and an
     * endpoint resolved through the context is already initialized - hence the endpoint is built directly, so
     * that the formatter is in place before the endpoint initializes.
     */
    public Endpoint logEndpoint(CamelContext context, String loggerName) {
        LogEndpoint endpoint = new LogEndpoint("log:" + loggerName + LOG_OPTIONS, context.getComponent("log"));
        endpoint.setCamelContext(context);
        endpoint.setLoggerName(loggerName);
        endpoint.setExchangeFormatter(this);
        endpoint.setShowAll(true);
        endpoint.setMultiline(true);
        return endpoint;
    }

    @Override
    protected Map<String, Object> filterHeaderAndProperties(Map<String, Object> map) {
        Map<String, Object> filtered = super.filterHeaderAndProperties(map);
        if (filtered == null || filtered.isEmpty()) {
            return filtered;
        }

        // NOTE: never modify the map in place - it may be the headers of the request about to be sent
        Map<String, Object> masked = new LinkedHashMap<>(filtered.size());
        for (Map.Entry<String, Object> entry : filtered.entrySet()) {
            masked.put(entry.getKey(), isSensitive(entry.getKey()) ? MASK : entry.getValue());
        }
        return masked;
    }

    private boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = normalize(key);
        return ALWAYS_MASKED.contains(normalized)
                || normalized.startsWith(OAUTH_HEADER_PREFIX)
                || maskedKeys.contains(normalized);
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
