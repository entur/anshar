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

package no.rutebanken.anshar.routes.outbound;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Deliveries without data are not posted - and must not be counted as failed posts (statusCode -1) in the metrics.
 */
public class EmptyOutboundDeliveryTest extends SpringBootBaseTest {

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private PrometheusMetricsService metricsService;

    @Test
    public void testEmptyDeliveryIsNotPostedOrCounted() throws Exception {
        final String subscriptionId = "empty-delivery-test";

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        consumer.start();

        try {
            OutboundSubscriptionSetup subscription = new OutboundSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE,
                    "http://localhost:" + consumer.getAddress().getPort() + "/siri", 0, new ArrayList<>(), subscriptionId);

            camelRouteManager.pushSiriData(sxDelivery(false), subscription, false, false);
            camelRouteManager.pushSiriData(sxDelivery(true), subscription, false, false);

            // Only the delivery with data is posted
            String body = received.poll(10, TimeUnit.SECONDS);
            assertNotNull(body);
            assertNull(received.poll(2, TimeUnit.SECONDS));

            Map<String, Double> postsByStatusCode = countPostsByStatusCode(subscriptionId);
            assertEquals(Map.of("200", 1.0), postsByStatusCode);
        } finally {
            consumer.stop(0);
        }
    }

    private Map<String, Double> countPostsByStatusCode(String subscriptionId) {
        Map<String, Double> counts = new HashMap<>();
        for (Meter meter : metricsService.getMeters()) {
            if (meter instanceof Counter counter
                    && meter.getId().getName().endsWith("subscription.outbound")
                    && subscriptionId.equals(meter.getId().getTag("subscriptionId"))) {
                counts.merge(meter.getId().getTag("statusCode"), counter.count(), Double::sum);
            }
        }
        return counts;
    }

    private static Siri sxDelivery(boolean withSituation) {
        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        if (withSituation) {
            situations.getPtSituationElements().add(new PtSituationElement());
        }
        delivery.setSituations(situations);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getSituationExchangeDeliveries().add(delivery);

        Siri siri = new Siri();
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}
