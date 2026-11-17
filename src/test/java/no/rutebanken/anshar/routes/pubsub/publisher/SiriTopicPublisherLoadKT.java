package no.rutebanken.anshar.routes.pubsub.publisher;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.EstimatedVehicleJourney;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Elements of a delivery must not wait for each other before they are handed to the Pub/Sub client, which is what
 * the Camel split did ({@code later_element_start} 100–500 ms in dev). Runs the real publisher against the Pub/Sub
 * emulator, so it is not part of {@code mvn test}:
 * <pre>
 * gcloud beta emulators pubsub start --project=test --host-port=localhost:8085
 * mvn test -Dtest=SiriTopicPublisherLoadKT -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
class SiriTopicPublisherLoadKT {

    private static final String EMULATOR = "localhost:8085";
    private static final int DELIVERIES_PER_SECOND = 30;
    private static final int LARGE_DELIVERY_JOURNEYS = 140;

    private final List<EstimatedVehicleJourney> journeys = TopicReferenceFixtures.bnrJourneys();

    @Test
    void elementsAreHandedToTheClientWithoutWaitingForEachOther() throws Exception {
        for (String topic : List.of("load.xml.et", "load.avro.et")) {
            createTopic(topic);
        }

        // A fresh publisher per phase, so each phase's timers hold only its own deliveries
        run(new PrometheusMetricsService(), 5, 0);

        PrometheusMetricsService small = new PrometheusMetricsService();
        run(small, 60, 0);
        Timer smallHandedOver = report("2-journey deliveries", small);

        PrometheusMetricsService mixed = new PrometheusMetricsService();
        run(mixed, 30, 100);
        report("with every 100th delivery of " + LARGE_DELIVERY_JOURNEYS + " journeys", mixed);

        assertTrue(smallHandedOver.max(TimeUnit.MILLISECONDS) < 50,
                "2-journey deliveries waited up to " + smallHandedOver.max(TimeUnit.MILLISECONDS) + " ms");
    }

    private void run(PrometheusMetricsService metrics, int seconds, int largeEvery) throws InterruptedException {
        SiriTopicPublisher publisher = publisher(metrics);
        try {
            run(publisher, seconds, largeEvery);
        } finally {
            publisher.shutdown();
        }
    }

    private SiriTopicPublisher publisher(PrometheusMetricsService metrics) {
        PubsubTopicSinkFactory sinkFactory = new PubsubTopicSinkFactory();
        ReflectionTestUtils.setField(sinkFactory, "emulatorHost", EMULATOR);
        SiriTopicPublisher publisher = new SiriTopicPublisher(sinkFactory, metrics,
                new SiriObjectFactory(java.time.Instant.now()), new MappingAdapterPresets(null));
        ReflectionTestUtils.setField(publisher, "directEnabled", true);
        ReflectionTestUtils.setField(publisher, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "etTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "etAvroEnabled", true);
        ReflectionTestUtils.setField(publisher, "etXmlTopic", "google-pubsub://test:load.xml.et");
        ReflectionTestUtils.setField(publisher, "etAvroTopic", "google-pubsub://test:load.avro.et");
        ReflectionTestUtils.setField(publisher, "threads", 4);
        ReflectionTestUtils.setField(publisher, "queueSize", 10_000);
        ReflectionTestUtils.setField(publisher, "drainTimeoutMillis", 10_000L);
        ProducerTemplate kafka = mock(ProducerTemplate.class);
        when(kafka.asyncRequestBodyAndHeaders(anyString(), any(), anyMap())).thenReturn(CompletableFuture.completedFuture(null));
        ReflectionTestUtils.setField(publisher, "kafkaProducer", kafka);
        publisher.init();
        return publisher;
    }

    /** Publishes {@link #DELIVERIES_PER_SECOND} deliveries of 2 journeys a second; every {@code largeEvery}th is large */
    private void run(SiriTopicPublisher publisher, int seconds, int largeEvery) throws InterruptedException {
        long start = System.nanoTime();
        int total = seconds * DELIVERIES_PER_SECOND;
        for (int sent = 0; sent < total; sent++) {
            long due = start + TimeUnit.SECONDS.toNanos(sent) / DELIVERIES_PER_SECOND;
            long wait = due - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
            boolean large = largeEvery > 0 && sent % largeEvery == largeEvery - 1;
            int size = large ? LARGE_DELIVERY_JOURNEYS : 2;
            int first = (sent * 2) % (journeys.size() - size);
            // Fresh objects, as SiriHandler hands over what the repository just returned
            List<EstimatedVehicleJourney> delivery = TopicReferenceFixtures.bnrJourneys().subList(first, first + size);
            publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, delivery, "BNR");
        }
        // Let queued work and outstanding publishes finish before reading the timers
        TimeUnit.SECONDS.sleep(5);
    }

    private static Timer report(String label, PrometheusMetricsService metrics) {
        Timer handedOver = metrics.find("app.anshar.latency.outbound.stage").tags("stage", "handed_to_client").timer();
        Timer xml = metrics.find("app.anshar.outbound.publish").tags("format", "xml").timer();
        System.out.printf("LOAD %-45s handed_to_client n=%d mean=%.1f ms max=%.1f ms | xml publish n=%d mean=%.1f ms max=%.1f ms%n",
                label, handedOver.count(), handedOver.mean(TimeUnit.MILLISECONDS), handedOver.max(TimeUnit.MILLISECONDS),
                xml.count(), xml.mean(TimeUnit.MILLISECONDS), xml.max(TimeUnit.MILLISECONDS));
        StringBuilder buckets = new StringBuilder("LOAD   handed_to_client cumulative:");
        for (CountAtBucket bucket : handedOver.takeSnapshot().histogramCounts()) {
            buckets.append(String.format(" <=%.0fms:%.0f", bucket.bucket(TimeUnit.MILLISECONDS), bucket.count()));
        }
        System.out.println(buckets);
        return handedOver;
    }

    private static void createTopic(String topic) throws Exception {
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://" + EMULATOR + "/v1/projects/test/topics/" + topic))
                .PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
