package no.rutebanken.anshar.routes.messaging;

import com.google.protobuf.Timestamp;
import io.micrometer.core.instrument.Timer;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.CamelRouteNames;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DATA_VM consumer of Anshar's internal VM queue records how long a message spent between
 * the PROXY pod publishing it and this pod receiving it. Tests run the queue on direct:, so the
 * Pub/Sub publish time is set by hand here.
 */
public class VmQueueLatencyMetricsTest extends SpringBootBaseTest {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private PrometheusMetricsService metrics;

    private Timer queueTimer() {
        return metrics.find("app.anshar.latency.queue").tag("dataType", "VEHICLE_MONITORING").timer();
    }

    @Test
    public void queueLatencyIsTimeFromPublishToConsume() {
        Timer before = queueTimer();
        long countBefore = before == null ? 0 : before.count();
        double millisBefore = before == null ? 0 : before.totalTime(TimeUnit.MILLISECONDS);

        Instant published = Instant.now().minusSeconds(3);
        producerTemplate.send("direct:" + CamelRouteNames.TRANSFORM_QUEUE_VM, exchange -> {
            exchange.getIn().setBody("not a real message");
            exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, Timestamp.newBuilder()
                    .setSeconds(published.getEpochSecond())
                    .setNanos(published.getNano())
                    .build());
            exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, Map.of("subscriptionId", "queue-latency-test"));
        });

        Timer queue = queueTimer();
        assertEquals(countBefore + 1, queue == null ? 0 : queue.count());
        double millis = queue.totalTime(TimeUnit.MILLISECONDS) - millisBefore;
        assertTrue(millis >= 3_000 && millis < 10_000, "queue latency was " + millis + " ms");
    }
}
