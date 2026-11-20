package no.rutebanken.anshar.outbound;

import com.hazelcast.map.IMap;
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.SubscriptionContextStructure;
import uk.org.siri.siri21.SubscriptionRequest;

import com.sun.net.httpserver.HttpServer;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutboundSubscriptionTest extends SpringBootBaseTest {

    @Autowired
    ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    @Qualifier("getFailTrackerMap")
    private IMap<String, Instant> failTrackerMap;

    @Autowired
    private Situations situations;

    @Autowired
    @Qualifier("getInitialDeliveryTimestampMap")
    private IMap<String, Instant> initialDeliveryTimestampMap;

    private static final String RESUBSCRIBE_DATASET = "RESUB";


    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 10000;

    @Value("${anshar.outbound.heartbeatinterval.maximum}")
    private long maximumHeartbeatInterval = 300000;

    @Test
    public void testHeartbeatInterval() throws DatatypeConfigurationException {
        final long tooShortDurationInMilliSeconds = minimumHeartbeatInterval - 1;
        final long tooLongDurationInMilliSeconds = maximumHeartbeatInterval + 1;

        SubscriptionRequest subscriptionRequestWithTooShortInterval = getSubscriptionRequest(tooShortDurationInMilliSeconds);
        long heartbeatInterval = serverSubscriptionManager.getHeartbeatInterval(subscriptionRequestWithTooShortInterval);

        assertTrue(tooShortDurationInMilliSeconds < minimumHeartbeatInterval);
        assertEquals(heartbeatInterval, minimumHeartbeatInterval);


        SubscriptionRequest subscriptionRequestWithTooLongInterval = getSubscriptionRequest(tooLongDurationInMilliSeconds);
        heartbeatInterval = serverSubscriptionManager.getHeartbeatInterval(subscriptionRequestWithTooLongInterval);

        assertTrue(tooLongDurationInMilliSeconds > maximumHeartbeatInterval);
        assertEquals(heartbeatInterval, maximumHeartbeatInterval);
    }

    @Test
    public void testDuplicateSubscriptionIds() throws JAXBException, XMLStreamException {
        final String subscriptionId = "36dfa2d0-51d7-42fb-b828-44fc07684239";
        String sxSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.2790513Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>https://0.0.0.0/api/siri/sx/36dfa2d0-51d7-42fb-b828-44fc07684239</ConsumerAddress>\n" +
                "\t\t<RequestorRef>GAG-SX-36dfa2d0-51d7-42fb-b828-44fc07684239</RequestorRef>\n" +
                "\t\t<MessageIdentifier>52467cd2-e469-4f37-8841-27c3a9b57b63</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<SituationExchangeSubscriptionRequest>\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.2785096Z</InitialTerminationTime>\n" +
                "\t\t\t<SituationExchangeRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.2787087Z</RequestTimestamp>\n" +
                "\t\t\t</SituationExchangeRequest>\n" +
                "\t\t</SituationExchangeSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";

        String etSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.5012071Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>https://0.0.0.0/api/siri/et/36dfa2d0-51d7-42fb-b828-44fc07684239</ConsumerAddress>\n" +
                "\t\t<RequestorRef>GAG-ET-36dfa2d0-51d7-42fb-b828-44fc07684239</RequestorRef>\n" +
                "\t\t<MessageIdentifier>444dde0d-3663-4de9-aa94-56ee17d86ba9</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<EstimatedTimetableSubscriptionRequest>\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.500977Z</InitialTerminationTime>\n" +
                "\t\t\t<EstimatedTimetableRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.5011831Z</RequestTimestamp>\n" +
                "\t\t\t</EstimatedTimetableRequest>\n" +
                "\t\t</EstimatedTimetableSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";


        final Siri siriSX = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(sxSubscription), null, OutboundIdMappingPolicy.DEFAULT, null);
        final Siri siriET = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(etSubscription), null, OutboundIdMappingPolicy.DEFAULT, null);

        assertNotNull(siriSX);
        assertNotNull(siriET);

        assertTrue(siriSX.getSubscriptionResponse() != null);
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses() != null);
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses().get(0).isStatus() != null);
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

        assertTrue(siriET.getSubscriptionResponse() != null);
        assertTrue(siriET.getSubscriptionResponse().getResponseStatuses() != null);
        assertTrue(siriET.getSubscriptionResponse().getResponseStatuses().get(0).isStatus() != null);
        assertFalse(siriET.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

        serverSubscriptionManager.terminateSubscription(subscriptionId, true);
    }

    @Test
    public void testTerminateSubscriptionResponseIsPosted() throws JAXBException, XMLStreamException, IOException, InterruptedException {
        final String subscriptionId = "terminate-response-test";

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        consumer.start();

        try {
            String sxSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.1\">\n" +
                    "\t<SubscriptionRequest>\n" +
                    "\t\t<RequestTimestamp>2019-12-06T14:38:42.2790513Z</RequestTimestamp>\n" +
                    "\t\t<ConsumerAddress>http://localhost:" + consumer.getAddress().getPort() + "/siri</ConsumerAddress>\n" +
                    "\t\t<RequestorRef>" + subscriptionId + "</RequestorRef>\n" +
                    "\t\t<MessageIdentifier>52467cd2-e469-4f37-8841-27c3a9b57b63</MessageIdentifier>\n" +
                    "\t\t<SubscriptionContext>\n" +
                    "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                    "\t\t</SubscriptionContext>\n" +
                    "\t\t<SituationExchangeSubscriptionRequest>\n" +
                    "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                    "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.2785096Z</InitialTerminationTime>\n" +
                    "\t\t\t<SituationExchangeRequest>\n" +
                    "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.2787087Z</RequestTimestamp>\n" +
                    "\t\t\t</SituationExchangeRequest>\n" +
                    "\t\t</SituationExchangeSubscriptionRequest>\n" +
                    "\t</SubscriptionRequest>\n" +
                    "</Siri>";

            final Siri response = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(sxSubscription), null, OutboundIdMappingPolicy.DEFAULT, null);
            assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

            serverSubscriptionManager.terminateSubscription(subscriptionId, true);

            boolean terminateResponseReceived = false;
            String body;
            while (!terminateResponseReceived && (body = received.poll(10, TimeUnit.SECONDS)) != null) {
                terminateResponseReceived = body.contains("TerminateSubscriptionResponse");
            }
            assertTrue(terminateResponseReceived);
        } finally {
            consumer.stop(0);
        }
    }

    @Test
    public void testResubscriptionToFailingRecipientIsOnlyAcceptedWhenReachable() throws Exception {
        final String subscriptionId = "failing-recipient-test";

        // Nothing listens on port 1
        final int unreachablePort = 1;

        // Previous deliveries to this subscription have failed - for longer than the grace period
        failTrackerMap.set(subscriptionId, Instant.now().minus(2, ChronoUnit.HOURS));

        // Recipient is still unreachable - rejected
        Siri response = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(sxSubscriptionRequest(subscriptionId, unreachablePort)),
                null, OutboundIdMappingPolicy.DEFAULT, null);
        assertFalse(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
        assertTrue(SiriXml.toXml(response).contains("ConsumerAddress is unreachable"));
        // Failures are remembered as long as the recipient keeps trying
        assertTrue(failTrackerMap.getEntryView(subscriptionId).getTtl() > 0);

        // Recipient responds, but still fails to handle deliveries
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        final int port = consumer.getAddress().getPort();
        consumer.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        consumer.start();

        try {
            // Recipient is reachable again - accepted
            response = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(sxSubscriptionRequest(subscriptionId, port)),
                    null, OutboundIdMappingPolicy.DEFAULT, null);
            assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

            // Failures are remembered until a delivery succeeds - so the next failure cancels the subscription
            serverSubscriptionManager.pushFailedForSubscription(subscriptionId);
            assertFalse(isActive(subscriptionId));

            // ...and are still remembered after the subscription is cancelled
            assertTrue(failTrackerMap.containsKey(subscriptionId));
        } finally {
            consumer.stop(0);
            failTrackerMap.delete(subscriptionId);
        }
    }

    @Test
    public void testFailingSubscriptionIsCancelledAfterGracePeriod() throws Exception {
        // Recipient responds, but fails to handle deliveries - so a successful push never clears the fail-tracker
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        final int port = consumer.getAddress().getPort();
        consumer.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        consumer.start();

        // HeartbeatInterval is raised to the 60s minimum in tests - 3 x 60s does not exceed the minimum grace period of 3 minutes
        final String withinGracePeriod = "grace-period-not-passed-test";
        final String afterGracePeriod = "grace-period-passed-test";
        try {
            for (String subscriptionId : List.of(withinGracePeriod, afterGracePeriod)) {
                Siri response = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(sxSubscriptionRequest(subscriptionId, port, "PT30S")),
                        null, OutboundIdMappingPolicy.DEFAULT, null);
                assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
            }

            failTrackerMap.set(withinGracePeriod, Instant.now().minus(2, ChronoUnit.MINUTES));
            serverSubscriptionManager.pushFailedForSubscription(withinGracePeriod);
            assertTrue(isActive(withinGracePeriod));

            failTrackerMap.set(afterGracePeriod, Instant.now().minus(4, ChronoUnit.MINUTES));
            serverSubscriptionManager.pushFailedForSubscription(afterGracePeriod);
            assertFalse(isActive(afterGracePeriod));
        } finally {
            consumer.stop(0);
            serverSubscriptionManager.terminateSubscription(withinGracePeriod, false);
            failTrackerMap.delete(withinGracePeriod);
            failTrackerMap.delete(afterGracePeriod);
        }
    }

    @Test
    public void testResubscriptionWithNewIdReplacesSubscriptionToSameAddress() throws Exception {
        final String first = "same-address-first";
        final String second = "same-address-second";
        final String otherAddress = "other-address";
        // Unreachable port - no deliveries are needed
        final int port = 1;
        try {
            for (String subscriptionId : List.of(first, otherAddress)) {
                Siri response = serverSubscriptionManager.handleSubscriptionRequest(
                        SiriXml.parseXml(sxSubscriptionRequest(subscriptionId, port, "PT60M", subscriptionId.equals(otherAddress) ? otherAddress : "shared")),
                        null, OutboundIdMappingPolicy.DEFAULT, null);
                assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
            }

            // Same recipient subscribes again with a new SubscriptionIdentifier, without terminating the first
            Siri response = serverSubscriptionManager.handleSubscriptionRequest(
                    SiriXml.parseXml(sxSubscriptionRequest(second, port, "PT60M", "shared")),
                    null, OutboundIdMappingPolicy.DEFAULT, null);
            assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

            assertFalse(isActive(first));
            assertTrue(isActive(second));
            assertTrue(isActive(otherAddress));
        } finally {
            for (String subscriptionId : List.of(first, second, otherAddress)) {
                serverSubscriptionManager.terminateSubscription(subscriptionId, false);
                failTrackerMap.delete(subscriptionId);
            }
        }
    }

    @Test
    public void testRecentInitialDeliveryIsNotRepeatedForResubscription() throws Exception {
        AtomicInteger initialDeliveries = new AtomicInteger();
        HttpServer consumer = initialDeliveryConsumer(initialDeliveries, () -> 200, 0);
        final int port = consumer.getAddress().getPort();
        situations.add(RESUBSCRIBE_DATASET, situation("resubscribe-situation"));

        final List<String> subscriptionIds = List.of("resubscribe-first", "resubscribe-second", "resubscribe-third");
        try {
            subscribe(subscriptionIds.get(0), port, "resubscribe");
            waitForCount(initialDeliveries, 1);
            waitFor(() -> initialDeliveryTimestampMap.containsKey(subscriptionIds.get(0)));

            // Re-subscription right after - the recipient already has all data
            subscribe(subscriptionIds.get(1), port, "resubscribe");
            Thread.sleep(2000);
            assertEquals(1, initialDeliveries.get());
            assertFalse(isActive(subscriptionIds.get(0)));
            assertTrue(isActive(subscriptionIds.get(1)));
            assertTrue(initialDeliveryTimestampMap.containsKey(subscriptionIds.get(1)));

            // Deliveries to the replaced subscription have failed since - updates may have been lost
            failTrackerMap.set(subscriptionIds.get(1), Instant.now());
            subscribe(subscriptionIds.get(2), port, "resubscribe");
            waitForCount(initialDeliveries, 2);
        } finally {
            cleanUp(consumer, subscriptionIds);
        }
    }

    @Test
    public void testResubscriptionDuringInitialDeliveryDependsOnItsOutcome() throws Exception {
        AtomicInteger initialDeliveries = new AtomicInteger();
        AtomicInteger responseCode = new AtomicInteger(200);
        // Initial deliveries are held, so that the re-subscription arrives while one is sent
        HttpServer consumer = initialDeliveryConsumer(initialDeliveries, responseCode::get, 1500);
        final int port = consumer.getAddress().getPort();
        situations.add(RESUBSCRIBE_DATASET, situation("resubscribe-situation"));

        final List<String> subscriptionIds = List.of("pending-delivered-first", "pending-delivered-second",
                "pending-failed-first", "pending-failed-second");
        try {
            // Delivered - the re-subscription relies on it
            subscribe(subscriptionIds.get(0), port, "pending-delivered");
            waitForCount(initialDeliveries, 1);
            subscribe(subscriptionIds.get(1), port, "pending-delivered");
            waitFor(() -> initialDeliveryTimestampMap.containsKey(subscriptionIds.get(1)));
            Thread.sleep(500);
            assertEquals(1, initialDeliveries.get());

            // Not delivered - the re-subscription gets its own
            responseCode.set(500);
            subscribe(subscriptionIds.get(2), port, "pending-failed");
            waitForCount(initialDeliveries, 2);
            responseCode.set(200);
            subscribe(subscriptionIds.get(3), port, "pending-failed");
            waitForCount(initialDeliveries, 3);
            waitFor(() -> initialDeliveryTimestampMap.containsKey(subscriptionIds.get(3)));
        } finally {
            cleanUp(consumer, subscriptionIds);
        }
    }

    /** Counts posts of the test situation, i.e. initial deliveries, and holds each one before responding */
    private static HttpServer initialDeliveryConsumer(AtomicInteger initialDeliveries, IntSupplier responseCode, long holdMillis) throws IOException {
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.setExecutor(Executors.newCachedThreadPool());
        consumer.createContext("/", exchange -> {
            int status = 200;
            if (new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).contains("resubscribe-situation")) {
                status = responseCode.getAsInt();
                initialDeliveries.incrementAndGet();
                try {
                    Thread.sleep(holdMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        consumer.start();
        return consumer;
    }

    private void cleanUp(HttpServer consumer, List<String> subscriptionIds) {
        consumer.stop(0);
        situations.clearAllByDatasetId(RESUBSCRIBE_DATASET);
        for (String subscriptionId : subscriptionIds) {
            serverSubscriptionManager.terminateSubscription(subscriptionId, false);
            failTrackerMap.delete(subscriptionId);
            initialDeliveryTimestampMap.delete(subscriptionId);
        }
    }

    private void subscribe(String subscriptionId, int port, String consumerPath) throws Exception {
        Siri response = serverSubscriptionManager.handleSubscriptionRequest(
                SiriXml.parseXml(sxSubscriptionRequest(subscriptionId, port, "PT60M", consumerPath)),
                RESUBSCRIBE_DATASET, OutboundIdMappingPolicy.DEFAULT, null);
        assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting");
    }

    private static void waitForCount(AtomicInteger counter, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (counter.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(expected, counter.get());
    }

    private static PtSituationElement situation(String situationNumber) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        SituationNumber number = new SituationNumber();
        number.setValue(situationNumber);
        element.setSituationNumber(number);
        RequestorRef participant = new RequestorRef();
        participant.setValue("RESUB");
        element.setParticipantRef(participant);
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(ZonedDateTime.now().minusHours(1));
        period.setEndTime(ZonedDateTime.now().plusHours(4));
        element.getValidityPeriods().add(period);
        return element;
    }

    private boolean isActive(String subscriptionId) {
        return serverSubscriptionManager.getSubscriptions().stream()
                .anyMatch(s -> subscriptionId.equals(((OutboundSubscriptionSetup) s).getSubscriptionId()));
    }

    private static String sxSubscriptionRequest(String subscriptionId, int port) {
        return sxSubscriptionRequest(subscriptionId, port, "PT60M");
    }

    private static String sxSubscriptionRequest(String subscriptionId, int port, String heartbeatInterval) {
        return sxSubscriptionRequest(subscriptionId, port, heartbeatInterval, subscriptionId);
    }

    private static String sxSubscriptionRequest(String subscriptionId, int port, String heartbeatInterval, String consumerPath) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.1\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.2790513Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>http://localhost:" + port + "/siri/" + consumerPath + "</ConsumerAddress>\n" +
                // Same client for the same address - as a client that re-subscribes
                "\t\t<RequestorRef>" + consumerPath + "</RequestorRef>\n" +
                "\t\t<MessageIdentifier>52467cd2-e469-4f37-8841-27c3a9b57b63</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>" + heartbeatInterval + "</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<SituationExchangeSubscriptionRequest>\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.2785096Z</InitialTerminationTime>\n" +
                "\t\t\t<SituationExchangeRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.2787087Z</RequestTimestamp>\n" +
                "\t\t\t</SituationExchangeRequest>\n" +
                "\t\t</SituationExchangeSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";
    }

    SubscriptionRequest getSubscriptionRequest(long heartbeatIntervalMillis) {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        SubscriptionContextStructure context = new SubscriptionContextStructure();

        context.setHeartbeatInterval(Duration.of(heartbeatIntervalMillis, ChronoUnit.MILLIS));

        subscriptionRequest.setSubscriptionContext(context);
        return subscriptionRequest;
    }
}
