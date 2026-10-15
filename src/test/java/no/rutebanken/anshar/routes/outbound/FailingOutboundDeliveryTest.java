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

import com.hazelcast.map.IMap;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SituationNumber;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailingOutboundDeliveryTest extends SpringBootBaseTest {

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private Situations situations;

    @Autowired
    @Qualifier("getHeartbeatTimestampMap")
    private IMap<String, Instant> heartbeatTimestampMap;

    @Value("${anshar.outbound.circuitbreaker.failure.threshold:5}")
    private int failureThreshold;

    @Value("${anshar.default.max.threads.per.outbound.subscription:5}")
    private int threadsPerSubscription;

    private HttpServer consumer;

    private String subscriptionId;

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.stop(0);
        }
        if (subscriptionId != null) {
            serverSubscriptionManager.terminateSubscription(subscriptionId, false);
            serverSubscriptionManager.clearFailTracker(subscriptionId);
        }
    }

    @Test
    public void testDeliveriesArePausedWhenRecipientKeepsFailing() throws Exception {
        AtomicInteger received = new AtomicInteger();
        OutboundSubscriptionSetup subscription = createSubscription("failing-delivery-test", () -> {
            received.incrementAndGet();
            return 500;
        }, null);

        for (int i = 0; i < 50; i++) {
            camelRouteManager.pushSiriData(sxDelivery("test"), subscription, false);
        }

        waitUntilNoMoreRequests(received);

        // Requests already in flight when the circuit opens may also reach the recipient
        assertTrue(received.get() >= failureThreshold, "Received " + received.get());
        assertTrue(received.get() < failureThreshold + threadsPerSubscription, "Received " + received.get());
    }

    @Test
    public void testRejectedDeliveriesDoNotPauseDeliveries() throws Exception {
        AtomicInteger received = new AtomicInteger();
        OutboundSubscriptionSetup subscription = createSubscription("rejected-delivery-test", () -> {
            received.incrementAndGet();
            return 400;
        }, null);

        final int deliveries = 3 * failureThreshold;
        for (int i = 0; i < deliveries; i++) {
            camelRouteManager.pushSiriData(sxDelivery("test"), subscription, false);
        }

        waitUntilNoMoreRequests(received);

        // Recipient is responding - circuit is not opened
        assertEquals(deliveries, received.get());
    }

    @Test
    public void testAllDataIsResentWhenRecipientRespondsAgain() throws Exception {
        final String datasetId = "resend-delivery-test";
        situations.add(datasetId, situation("resend-situation"));

        AtomicInteger received = new AtomicInteger();
        BlockingQueue<String> bodies = new LinkedBlockingQueue<>();
        OutboundSubscriptionSetup subscription = createSubscription(datasetId, () ->
                received.incrementAndGet() <= failureThreshold ? 500 : 200, bodies);

        Object initialBackoff = ReflectionTestUtils.getField(camelRouteManager, "circuitBreakerInitialBackoffSeconds");
        final int backoffSeconds = 2;
        ReflectionTestUtils.setField(camelRouteManager, "circuitBreakerInitialBackoffSeconds", backoffSeconds);
        try {
            for (int i = 0; i < failureThreshold; i++) {
                camelRouteManager.pushSiriData(sxDelivery("update"), subscription, false);
            }
            // The recipient may have received all requests before the failures are recorded - wait for the circuit to open
            OutboundCircuitBreaker circuitBreaker = circuitBreaker(subscription);
            long deadline = System.currentTimeMillis() + 10000;
            while (circuitBreaker.getConsecutiveFailures() < failureThreshold && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(failureThreshold, received.get());
            assertEquals(failureThreshold, circuitBreaker.getConsecutiveFailures());

            // Skipped while circuit is open
            camelRouteManager.pushSiriData(sxDelivery("skipped"), subscription, false);

            Thread.sleep(backoffSeconds * 1000 + 500);
            // Probe succeeds
            camelRouteManager.pushSiriData(sxDelivery("probe"), subscription, false);

            boolean resent = false;
            String body;
            while (!resent && (body = bodies.poll(10, TimeUnit.SECONDS)) != null) {
                assertFalse(body.contains("skipped"));
                resent = body.contains("resend-situation");
            }
            assertTrue(resent, "All current data should be resent when the circuit closes");
        } finally {
            ReflectionTestUtils.setField(camelRouteManager, "circuitBreakerInitialBackoffSeconds", initialBackoff);
        }
    }

    @Test
    public void testProbeConsumerAddress() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            // Any response is accepted
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });
        server.start();
        try {
            assertNull(camelRouteManager.probeConsumerAddress("http://localhost:" + server.getAddress().getPort() + "/siri"));
        } finally {
            server.stop(0);
        }

        // Nothing listens on port 1
        String error = camelRouteManager.probeConsumerAddress("http://localhost:1/siri");
        assertNotNull(error);
        assertTrue(error.contains("ConnectException"), error);
    }

    @Test
    public void testProbeErrorDescribesWholeCauseChain() {
        // On Linux the HttpClient reports a refused connection as a ConnectException caused by a ClosedChannelException
        ConnectException refused = new ConnectException();
        refused.initCause(new ClosedChannelException());

        String error = CamelRouteManager.describeError(new IOException(refused));

        assertTrue(error.contains("ConnectException"), error);
        assertTrue(error.contains("ClosedChannelException"), error);
    }

    @Test
    public void testNonTransientErrors() {
        SSLHandshakeException invalidCertificate = new SSLHandshakeException("(certificate_unknown) No subject alternative DNS name matching");
        invalidCertificate.initCause(new CertificateException("No subject alternative DNS name matching"));
        assertTrue(CamelRouteManager.isNonTransientError(invalidCertificate));
        assertTrue(CamelRouteManager.isNonTransientError(new RuntimeException(invalidCertificate)));

        SSLHandshakeException terminated = new SSLHandshakeException("Remote host terminated the handshake");
        terminated.initCause(new EOFException("SSL peer shut down incorrectly"));
        assertFalse(CamelRouteManager.isNonTransientError(terminated));
        assertFalse(CamelRouteManager.isNonTransientError(new IOException(new UnknownHostException("unknown.host"))));
        assertFalse(CamelRouteManager.isNonTransientError(new HttpConnectTimeoutException("HTTP connect timed out")));
        assertFalse(CamelRouteManager.isNonTransientError(new ConnectException("Connection refused")));
        assertFalse(CamelRouteManager.isNonTransientError(new IOException("Connection reset")));
    }

    /**
     * Registers an active subscription (with its own datasetId to not receive data from other tests) to a local recipient
     */
    private OutboundSubscriptionSetup createSubscription(String id, IntSupplier responseCode, BlockingQueue<String> bodies) throws IOException {
        consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = responseCode.getAsInt();
            if (bodies != null && status < 300) {
                bodies.add(body);
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        consumer.start();

        subscriptionId = id;
        OutboundSubscriptionSetup subscription = new OutboundSubscriptionSetup(ZonedDateTime.now(), SiriDataType.SITUATION_EXCHANGE,
                "http://localhost:" + consumer.getAddress().getPort() + "/siri", 3600000, 0, null, new ArrayList<>(),
                id, id, ZonedDateTime.now().plusHours(1), id, null, null);
        // No heartbeats during the test - they would be counted as requests
        heartbeatTimestampMap.put(id, Instant.now(), 1, TimeUnit.HOURS);
        // Registered to avoid the cleanup-job removing the circuit breaker during the test
        serverSubscriptionManager.subscriptions.put(id, subscription);
        return subscription;
    }

    /**
     * Waits for the circuit breaker of the subscription - it is created when the first delivery is posted
     */
    @SuppressWarnings("unchecked")
    private OutboundCircuitBreaker circuitBreaker(OutboundSubscriptionSetup subscription) throws InterruptedException {
        Map<String, OutboundCircuitBreaker> circuitBreakers =
                (Map<String, OutboundCircuitBreaker>) ReflectionTestUtils.getField(camelRouteManager, "circuitBreakers");
        long deadline = System.currentTimeMillis() + 10000;
        while (!circuitBreakers.containsKey(subscription.getSubscriptionId()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        OutboundCircuitBreaker circuitBreaker = circuitBreakers.get(subscription.getSubscriptionId());
        assertNotNull(circuitBreaker, "Circuit breaker created");
        return circuitBreaker;
    }

    private static void waitUntilNoMoreRequests(AtomicInteger received) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (received.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        int previous;
        do {
            previous = received.get();
            Thread.sleep(1000);
        } while (received.get() != previous);
    }

    private static PtSituationElement situation(String situationNumber) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        SituationNumber number = new SituationNumber();
        number.setValue(situationNumber);
        element.setSituationNumber(number);
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(ZonedDateTime.now().minusHours(1));
        period.setEndTime(ZonedDateTime.now().plusHours(4));
        element.getValidityPeriods().add(period);
        return element;
    }

    private static Siri sxDelivery(String situationNumber) {
        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(situation(situationNumber));
        delivery.setSituations(situations);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getSituationExchangeDeliveries().add(delivery);

        Siri siri = new Siri();
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}
