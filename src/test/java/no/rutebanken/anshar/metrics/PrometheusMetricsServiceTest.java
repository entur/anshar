package no.rutebanken.anshar.metrics;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PrometheusMetricsServiceTest extends SpringBootBaseTest {

    @Autowired
    private PrometheusMetricsService metricsService;

    @Test
    public void testOutboundCounterIsRemovedWhenSubscriptionIsGone() {
        final String subscriptionId = "no-longer-existing-subscription";

        metricsService.markPostToSubscription(SiriDataType.ESTIMATED_TIMETABLE, SubscriptionSetup.SubscriptionMode.SUBSCRIBE, subscriptionId, 200);

        assertNotNull(metricsService.find("app.anshar.subscription.outbound").tag("subscriptionId", subscriptionId).counter());

        metricsService.update();

        assertNull(metricsService.find("app.anshar.subscription.outbound").tag("subscriptionId", subscriptionId).counter());
    }
}
