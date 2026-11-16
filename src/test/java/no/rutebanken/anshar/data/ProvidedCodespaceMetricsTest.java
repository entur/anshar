package no.rutebanken.anshar.data;

import io.micrometer.core.instrument.Counter;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.LocationStructure;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With useProvidedCodespaceId, elements without a codespace of their own (DataSource or
 * ParticipantRef) are dropped before storage. That drop is counted against the subscription's
 * dataset, both in data.dropped and in the received-data totals.
 */
public class ProvidedCodespaceMetricsTest extends SpringBootBaseTest {

    @Autowired
    private SubscriptionManager manager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SiriObjectFactory factory;

    @Autowired
    private PrometheusMetricsService metrics;

    private static VehicleActivityStructure vehicle(String dataSource) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(ZonedDateTime.now());
        element.setValidUntilTime(ZonedDateTime.now().plusMinutes(1));

        VehicleActivityStructure.MonitoredVehicleJourney journey = new VehicleActivityStructure.MonitoredVehicleJourney();
        journey.setDataSource(dataSource);
        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(59.91));
        location.setLongitude(BigDecimal.valueOf(10.75));
        journey.setVehicleLocation(location);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("TST:Vehicle:" + UUID.randomUUID());
        journey.setVehicleRef(vehicleRef);

        element.setMonitoredVehicleJourney(journey);
        return element;
    }

    @Test
    public void vehicleWithoutDataSourceIsCountedAsDropped() throws Exception {
        String datasetId = "DS" + UUID.randomUUID().toString().substring(0, 8);
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setSubscriptionId(datasetId + ":VM");
        subscription.setDatasetId(datasetId);
        subscription.setSubscriptionType(SiriDataType.VEHICLE_MONITORING);
        subscription.setUseProvidedCodespaceId(true);
        manager.addSubscription(subscription.getSubscriptionId(), subscription);

        try {
            String xml = SiriXml.toXml(factory.createVMServiceDelivery(List.of(vehicle("RUT"), vehicle(null))));
            handler.handleIncomingSiri(subscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

            Counter dropped = metrics.find("app.anshar.data.dropped")
                    .tags("dataType", "VEHICLE_MONITORING", "codespace", datasetId, "reason", "no_datasource")
                    .counter();
            assertEquals(1, dropped == null ? 0 : dropped.count());

            // Dropped before storage, so it must also be in the received-data totals
            Counter total = metrics.find("app.anshar.data.total").tags("dataType", "VEHICLE_MONITORING", "agency", datasetId).counter();
            Counter ignored = metrics.find("app.anshar.data.ignored").tags("dataType", "VEHICLE_MONITORING", "agency", datasetId).counter();
            assertEquals(1, total == null ? 0 : total.count(), "dropped element missing from data.total");
            assertEquals(1, ignored == null ? 0 : ignored.count(), "dropped element missing from data.ignored");
        } finally {
            manager.removeSubscription(subscription.getSubscriptionId());
        }
    }

    private double counter(String name, String... tags) {
        Counter counter = metrics.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private void assertDroppedBeforeStorage(String dataType, String datasetId, String reason) {
        assertEquals(1, counter("app.anshar.data.dropped", "dataType", dataType, "codespace", datasetId, "reason", reason));
        assertEquals(1, counter("app.anshar.data.total", "dataType", dataType, "agency", datasetId), "dropped element missing from data.total");
        assertEquals(1, counter("app.anshar.data.ignored", "dataType", dataType, "agency", datasetId), "dropped element missing from data.ignored");
    }

    private SubscriptionSetup subscription(String datasetId, SiriDataType type) {
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setSubscriptionId(datasetId + ":" + type);
        subscription.setDatasetId(datasetId);
        subscription.setSubscriptionType(type);
        subscription.setUseProvidedCodespaceId(true);
        manager.addSubscription(subscription.getSubscriptionId(), subscription);
        return subscription;
    }

    private static EstimatedVehicleJourney journey(String dataSource) {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setDataSource(dataSource);
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        journey.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("TST:Vehicle:" + UUID.randomUUID());
        journey.setVehicleRef(vehicleRef);
        journey.setRecordedAtTime(ZonedDateTime.now());

        EstimatedCall call = new EstimatedCall();
        StopPointRefStructure stop = new StopPointRefStructure();
        stop.setValue("NSR:TEST:1");
        call.setStopPointRef(stop);
        call.setAimedArrivalTime(ZonedDateTime.now().plusHours(1));
        call.setOrder(BigInteger.ONE);
        EstimatedVehicleJourney.EstimatedCalls calls = new EstimatedVehicleJourney.EstimatedCalls();
        calls.getEstimatedCalls().add(call);
        journey.setEstimatedCalls(calls);
        return journey;
    }

    @Test
    public void journeyWithoutDataSourceIsCountedAsDropped() throws Exception {
        String datasetId = "DS" + UUID.randomUUID().toString().substring(0, 8);
        SubscriptionSetup subscription = subscription(datasetId, SiriDataType.ESTIMATED_TIMETABLE);
        try {
            String xml = SiriXml.toXml(factory.createETServiceDelivery(List.of(journey("RUT"), journey(null))));
            handler.handleIncomingSiri(subscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

            assertDroppedBeforeStorage("ESTIMATED_TIMETABLE", datasetId, "no_datasource");
        } finally {
            manager.removeSubscription(subscription.getSubscriptionId());
        }
    }
}
