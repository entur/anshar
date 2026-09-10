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
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaskedHeadersExchangeFormatterTest {

    private static final String SECRET = "sEcReT-vAlUe";

    private final CamelContext context = new DefaultCamelContext();

    private final MaskedHeadersExchangeFormatter formatter =
            new MaskedHeadersExchangeFormatter(List.of("Ocp-Apim-Subscription-Key", "ET-Client-Name"));

    @Test
    public void masksValuesOfConfiguredCustomHeaders() {
        Exchange e = exchangeWithHeader("Ocp-Apim-Subscription-Key", SECRET);

        String formatted = formatter.format(e);

        assertTrue(formatted.contains("Ocp-Apim-Subscription-Key"), "header key should still be logged: " + formatted);
        assertTrue(formatted.contains(MaskedHeadersExchangeFormatter.MASK), "value should be masked: " + formatted);
        assertFalse(formatted.contains(SECRET), "header value must not be logged: " + formatted);
    }

    @Test
    public void masksConfiguredCustomHeadersRegardlessOfCase() {
        Exchange e = exchangeWithHeader("ocp-apim-SUBSCRIPTION-key", SECRET);

        String formatted = formatter.format(e);

        assertFalse(formatted.contains(SECRET), "header value must not be logged: " + formatted);
    }

    @Test
    public void masksAuthorizationHeader() {
        Exchange e = exchangeWithHeader("Authorization", "Bearer " + SECRET);

        String formatted = formatter.format(e);

        assertTrue(formatted.contains("Authorization"), "header key should still be logged: " + formatted);
        assertFalse(formatted.contains(SECRET), "token must not be logged: " + formatted);
    }

    @Test
    public void masksProxyAuthorizationHeader() {
        Exchange e = exchangeWithHeader("Proxy-Authorization", "Basic " + SECRET);

        assertFalse(formatter.format(e).contains(SECRET));
    }

    @Test
    public void masksOauthConfigHeaders() {
        Exchange e = exchangeWithHeader("oauth-client-secret", SECRET);
        e.getMessage().setHeader("oauth-client-id", "client-" + SECRET);

        String formatted = formatter.format(e);

        assertTrue(formatted.contains("oauth-client-secret"), "header key should still be logged: " + formatted);
        assertFalse(formatted.contains(SECRET), "oauth values must not be logged: " + formatted);
    }

    @Test
    public void keepsNonSensitiveHeaderValues() {
        Exchange e = exchangeWithHeader("CamelHttpMethod", "POST");

        String formatted = formatter.format(e);

        assertTrue(formatted.contains("CamelHttpMethod=POST"), "unrelated headers should be logged as-is: " + formatted);
    }

    @Test
    public void doesNotModifyTheHeadersOnTheExchange() {
        Exchange e = exchangeWithHeader("Authorization", "Bearer " + SECRET);

        formatter.format(e);

        assertTrue(("Bearer " + SECRET).equals(e.getMessage().getHeader("Authorization")),
                "formatting must leave the outgoing request untouched");
    }

    @Test
    public void masksSensitiveValuesHeldAsExchangeProperties() {
        Exchange e = exchangeWithHeader("CamelHttpMethod", "POST");
        e.setProperty("Authorization", "Bearer " + SECRET);

        assertFalse(formatter.format(e).contains(SECRET));
    }

    private Exchange exchangeWithHeader(String key, String value) {
        Exchange e = new DefaultExchange(context);
        e.getMessage().setBody("<Siri/>");
        e.getMessage().setHeader(key, value);
        return e;
    }
}
