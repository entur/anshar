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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the log-endpoints actually pick up the masking formatter through the exchangeFormatterRef,
 * and that the options configured on the formatter-bean still apply.
 */
public class MaskedHeadersLogEndpointTest {

    private static final String LOGGER_NAME = "masked.headers.test";
    private static final String SECRET = "sEcReT-vAlUe";

    private DefaultCamelContext context;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    public void setUp() throws Exception {
        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.addAppender(appender);

        MaskedHeadersExchangeFormatter formatter =
                new MaskedHeadersExchangeFormatter(List.of("Ocp-Apim-Subscription-Key"));

        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to(formatter.logEndpoint(getContext(), LOGGER_NAME));
            }
        });
        context.start();
    }

    @AfterEach
    public void tearDown() {
        logger.detachAppender(appender);
        context.stop();
    }

    @Test
    public void sensitiveHeadersAreMaskedInTheLoggedRequest() {
        String logged = sendRequest();

        assertFalse(logged.contains(SECRET), "no credential may reach the log: " + logged);
        assertTrue(logged.contains("Ocp-Apim-Subscription-Key=" + MaskedHeadersExchangeFormatter.MASK),
                "custom header should be logged as set: " + logged);
        assertTrue(logged.contains("Authorization=" + MaskedHeadersExchangeFormatter.MASK),
                "authorization should be logged as set: " + logged);
    }

    @Test
    public void remainingRequestDetailsAreStillLogged() {
        String logged = sendRequest();

        assertTrue(logged.contains("ET-Client-Name=anshar"), "unrelated headers should be intact: " + logged);
        assertTrue(logged.contains("<Siri/>"), "body should still be logged: " + logged);
        assertTrue(logged.contains("Headers"), "showAll should still apply from the formatter: " + logged);
    }

    private String sendRequest() {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            template.sendBodyAndHeaders("direct:in", "<Siri/>", Map.of(
                    "Ocp-Apim-Subscription-Key", SECRET,
                    "Authorization", "Bearer " + SECRET,
                    "ET-Client-Name", "anshar"
            ));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }
}
