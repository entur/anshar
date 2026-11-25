package no.rutebanken.anshar.routes.outbound;

import com.sun.net.httpserver.HttpServer;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A subscription made over HTTP with an UpdateInterval gets several pushes delivered as one post
 */
public class OutboundBatchingIntegrationTest extends SpringBootBaseTest {

    private static final String DATASET = "BATCHIT";
    private static final String SUBSCRIPTION_ID = "batching-integration-test";

    @Value("${anshar.incoming.port}")
    private int port;

    @Autowired
    private ServerSubscriptionManager manager;

    @Test
    void severalSingleObjectDeliveriesArriveAsOnePost() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        consumer.start();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/anshar/subscribe/" + DATASET))
                            .header("Content-Type", "application/xml")
                            .POST(HttpRequest.BodyPublishers.ofString(subscriptionRequest(consumer.getAddress().getPort())))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("SubscriptionResponse"), response.body());
            assertTrue(response.body().contains("<Status>true</Status>"), response.body());
            assertEquals(1000, manager.subscriptions.get(SUBSCRIPTION_ID).getUpdateInterval());

            for (String vehicle : List.of("veh-1", "veh-2", "veh-3")) {
                manager.pushUpdatesAsync(SiriDataType.VEHICLE_MONITORING, List.of(activity(vehicle)), DATASET);
            }

            List<String> deliveries = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5000;
            String body;
            while (deliveries.isEmpty() && (body = received.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS)) != null) {
                // Heartbeats and the like are not updates
                if (body.contains("VehicleMonitoringDelivery")) {
                    deliveries.add(body);
                }
            }
            assertEquals(1, deliveries.size(), "No delivery within 5 seconds");
            assertEquals(3, countOccurrences(deliveries.get(0), "<VehicleActivity>"), deliveries.get(0));

            // Everything was in the one post
            while ((body = received.poll(2, TimeUnit.SECONDS)) != null) {
                assertTrue(!body.contains("VehicleMonitoringDelivery"), "Unexpected second update post: " + body);
            }
        } finally {
            manager.terminateSubscription(SUBSCRIPTION_ID, false);
            consumer.stop(0);
        }
    }

    private static int countOccurrences(String body, String needle) {
        return body.split(needle, -1).length - 1;
    }

    private static VehicleActivityStructure activity(String vehicleRef) {
        ZonedDateTime now = ZonedDateTime.now();
        VehicleActivityStructure activity = new VehicleActivityStructure();
        activity.setRecordedAtTime(now);
        activity.setValidUntilTime(now.plusMinutes(10));
        VehicleActivityStructure.MonitoredVehicleJourney journey = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef ref = new VehicleRef();
        ref.setValue(vehicleRef);
        journey.setVehicleRef(ref);
        activity.setMonitoredVehicleJourney(journey);
        return activity;
    }

    private static String subscriptionRequest(int consumerPort) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "  <SubscriptionRequest>\n" +
                "    <RequestTimestamp>2026-11-25T10:00:00Z</RequestTimestamp>\n" +
                "    <ConsumerAddress>http://localhost:" + consumerPort + "/siri</ConsumerAddress>\n" +
                "    <RequestorRef>" + SUBSCRIPTION_ID + "</RequestorRef>\n" +
                "    <MessageIdentifier>52467cd2-e469-4f37-8841-27c3a9b57b63</MessageIdentifier>\n" +
                "    <SubscriptionContext>\n" +
                "      <HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "    </SubscriptionContext>\n" +
                "    <VehicleMonitoringSubscriptionRequest>\n" +
                "      <SubscriptionIdentifier>" + SUBSCRIPTION_ID + "</SubscriptionIdentifier>\n" +
                "      <InitialTerminationTime>2119-12-06T14:38:42Z</InitialTerminationTime>\n" +
                "      <VehicleMonitoringRequest version=\"2.0\">\n" +
                "        <RequestTimestamp>2026-11-25T10:00:00Z</RequestTimestamp>\n" +
                "      </VehicleMonitoringRequest>\n" +
                "      <UpdateInterval>PT1S</UpdateInterval>\n" +
                "    </VehicleMonitoringSubscriptionRequest>\n" +
                "  </SubscriptionRequest>\n" +
                "</Siri>";
    }
}
