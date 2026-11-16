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
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With useProvidedCodespaceId, VehicleActivities without a DataSource cannot be assigned a
 * codespace and are dropped. That drop is counted against the subscription's dataset.
 */
public class VehicleMonitoringDataSourceMetricsTest extends SpringBootBaseTest {

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
}
