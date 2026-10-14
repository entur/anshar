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
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailingOutboundDeliveryTest extends SpringBootBaseTest {

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Value("${anshar.outbound.circuitbreaker.failure.threshold:5}")
    private int failureThreshold;

    @Value("${anshar.default.max.threads.per.outbound.subscription:5}")
    private int threadsPerSubscription;

    @Test
    public void testDeliveriesArePausedWhenRecipientKeepsFailing() throws Exception {
        final String subscriptionId = "failing-delivery-test";

        AtomicInteger received = new AtomicInteger();
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            received.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        consumer.start();

        try {
            OutboundSubscriptionSetup subscription = new OutboundSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE,
                    "http://localhost:" + consumer.getAddress().getPort() + "/siri", 0, new ArrayList<>(), subscriptionId);

            final int deliveries = 50;
            for (int i = 0; i < deliveries; i++) {
                camelRouteManager.pushSiriData(sxDelivery(), subscription, false, false);
            }

            // Wait until no more requests arrive
            int previous;
            do {
                previous = received.get();
                Thread.sleep(1000);
            } while (received.get() != previous);

            // Requests already in flight when the circuit opens may also reach the recipient
            assertTrue(received.get() >= failureThreshold, "Received " + received.get());
            assertTrue(received.get() < failureThreshold + threadsPerSubscription, "Received " + received.get());
        } finally {
            consumer.stop(0);
        }
    }

    @Test
    public void testProbeConsumerAddress() throws Exception {
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/", exchange -> {
            // Any response is accepted
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });
        consumer.start();
        final int port = consumer.getAddress().getPort();
        try {
            assertNull(camelRouteManager.probeConsumerAddress("http://localhost:" + port + "/siri"));
        } finally {
            consumer.stop(0);
        }

        String error = camelRouteManager.probeConsumerAddress("http://localhost:" + port + "/siri");
        assertNotNull(error);
        assertTrue(error.contains("ConnectException"), error);
    }

    @Test
    public void testNonTransientErrors() {
        assertTrue(CamelRouteManager.isNonTransientError(new SSLHandshakeException("No subject alternative DNS name matching")));
        assertTrue(CamelRouteManager.isNonTransientError(new RuntimeException(new SSLHandshakeException("certificate_unknown"))));
        assertTrue(CamelRouteManager.isNonTransientError(new IOException(new UnknownHostException("unknown.host"))));

        assertFalse(CamelRouteManager.isNonTransientError(new HttpConnectTimeoutException("HTTP connect timed out")));
        assertFalse(CamelRouteManager.isNonTransientError(new ConnectException("Connection refused")));
        assertFalse(CamelRouteManager.isNonTransientError(new IOException("Connection reset")));
    }

    private static Siri sxDelivery() {
        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(new PtSituationElement());
        delivery.setSituations(situations);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getSituationExchangeDeliveries().add(delivery);

        Siri siri = new Siri();
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}
