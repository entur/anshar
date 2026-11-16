package no.rutebanken.anshar.routes.outbound;

import io.micrometer.core.instrument.Timer;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbound latency covers the time from Anshar storing VM updates until they are published to
 * the outbound Pub/Sub topic: the async push pool, the split and Avro conversion, and the publish.
 * The storage time travels with the exchange from pushUpdatesAsync to the publish route.
 */
public class VmOutboundLatencyMetricsTest extends SpringBootBaseTest {

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private PrometheusMetricsService metrics;

    private static VehicleActivityStructure vehicle() {
        VehicleActivityStructure activity = new VehicleActivityStructure();
        VehicleActivityStructure.MonitoredVehicleJourney journey = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef ref = new VehicleRef();
        ref.setValue("TST:Vehicle:outbound");
        journey.setVehicleRef(ref);
        activity.setMonitoredVehicleJourney(journey);
        return activity;
    }

    @Test
    public void pushedVmDeliveryCarriesTheTimeItWasStored() throws Exception {
        MockEndpoint topic = camelContext.getEndpoint("mock:vm-outbound-latency-test", MockEndpoint.class);
        topic.reset();
        topic.expectedMessageCount(1);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.setDefaultEndpointUri(topic.getEndpointUri());

        Object originalEnabled = ReflectionTestUtils.getField(serverSubscriptionManager, "pushToVmTopicEnabled");
        Object originalProducer = ReflectionTestUtils.getField(serverSubscriptionManager, "siriVmTopicProducer");
        ReflectionTestUtils.setField(serverSubscriptionManager, "pushToVmTopicEnabled", true);
        ReflectionTestUtils.setField(serverSubscriptionManager, "siriVmTopicProducer", template);
        try {
            long before = System.currentTimeMillis();
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.VEHICLE_MONITORING, List.of(vehicle()), "TST");

            topic.assertIsSatisfied(5_000);
            Long storedAt = topic.getExchanges().get(0).getIn()
                    .getHeader(PrometheusMetricsService.STORED_AT_HEADER, Long.class);
            assertNotNull(storedAt, "stored-at header missing");
            assertTrue(storedAt >= before && storedAt <= System.currentTimeMillis());
        } finally {
            ReflectionTestUtils.setField(serverSubscriptionManager, "pushToVmTopicEnabled", originalEnabled);
            ReflectionTestUtils.setField(serverSubscriptionManager, "siriVmTopicProducer", originalProducer);
        }
    }

    @Test
    public void outboundLatencyIsRecordedFromTheStoredTime() throws Exception {
        Timer timerBefore = metrics.find("app.anshar.latency.outbound").tag("dataType", "VEHICLE_MONITORING").timer();
        long countBefore = timerBefore == null ? 0 : timerBefore.count();
        double millisBefore = timerBefore == null ? 0 : timerBefore.totalTime(TimeUnit.MILLISECONDS);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(PrometheusMetricsService.STORED_AT_HEADER, System.currentTimeMillis() - 2_000);
        metrics.outboundLatencyRecorder(SiriDataType.VEHICLE_MONITORING).process(exchange);

        Timer outbound = metrics.find("app.anshar.latency.outbound").tag("dataType", "VEHICLE_MONITORING").timer();
        assertNotNull(outbound, "outbound latency timer not registered");
        assertEquals(countBefore + 1, outbound.count());
        double millis = outbound.totalTime(TimeUnit.MILLISECONDS) - millisBefore;
        assertTrue(millis >= 2_000 && millis < 10_000, "outbound latency was " + millis + " ms");
    }

    @Test
    public void exchangeWithoutStoredTimeIsNotRecorded() throws Exception {
        Timer timerBefore = metrics.find("app.anshar.latency.outbound").tag("dataType", "VEHICLE_MONITORING").timer();
        long countBefore = timerBefore == null ? 0 : timerBefore.count();

        metrics.outboundLatencyRecorder(SiriDataType.VEHICLE_MONITORING).process(new DefaultExchange(camelContext));

        Timer outbound = metrics.find("app.anshar.latency.outbound").tag("dataType", "VEHICLE_MONITORING").timer();
        assertEquals(countBefore, outbound == null ? 0 : outbound.count());
    }
}
