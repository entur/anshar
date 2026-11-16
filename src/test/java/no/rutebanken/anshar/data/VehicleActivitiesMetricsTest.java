package no.rutebanken.anshar.data;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.LocationStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every VM element in a delivery is accounted for exactly once: stored (success), expired or
 * ignored, and every element not stored is counted in app.anshar.data.dropped with its reason.
 * Each test uses its own codespace, since the metrics registry is shared by the Spring context.
 */
public class VehicleActivitiesMetricsTest extends SpringBootBaseTest {

    private static final String VM = "VEHICLE_MONITORING";

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private PrometheusMetricsService metrics;

    private String codespace;

    @BeforeEach
    public void init() {
        vehicleActivities.clearAll();
        codespace = "M" + UUID.randomUUID().toString().substring(0, 8);
    }

    private double counter(String name, String... tags) {
        Counter counter = metrics.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private double dropped(String reason) {
        return counter("app.anshar.data.dropped", "dataType", VM, "codespace", codespace, "reason", reason);
    }

    private double total() {
        return counter("app.anshar.data.total", "dataType", VM, "agency", codespace);
    }

    private double success() {
        return counter("app.anshar.data.success", "dataType", VM, "agency", codespace);
    }

    private double expired() {
        return counter("app.anshar.data.expired", "dataType", VM, "agency", codespace);
    }

    private double ignored() {
        return counter("app.anshar.data.ignored", "dataType", VM, "agency", codespace);
    }

    private static VehicleActivityStructure vehicle(String vehicleRef, ZonedDateTime recordedAt, double lat, double lon) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(recordedAt);
        element.setValidUntilTime(recordedAt.plusMinutes(10));

        VehicleActivityStructure.MonitoredVehicleJourney journey = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef ref = new VehicleRef();
        ref.setValue(vehicleRef);
        journey.setVehicleRef(ref);
        journey.setLocationRecordedAtTime(recordedAt);

        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(lat));
        location.setLongitude(BigDecimal.valueOf(lon));
        journey.setVehicleLocation(location);

        element.setMonitoredVehicleJourney(journey);
        return element;
    }

    private static VehicleActivityStructure vehicle(String vehicleRef) {
        return vehicle(vehicleRef, ZonedDateTime.now(), 59.91, 10.75);
    }

    /** The duplicate check reads a cache fed asynchronously by Hazelcast entry listeners */
    private void awaitCached(int size) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (vehicleActivities.cache.size() < size && System.currentTimeMillis() < deadline) {
            sleep(20);
        }
    }

    @Test
    public void invalidStructureIsCountedAsDroppedAndIgnored() {
        VehicleActivityStructure noVehicleRef = vehicle("v1");
        noVehicleRef.getMonitoredVehicleJourney().setVehicleRef(null);

        vehicleActivities.addAll(codespace, List.of(noVehicleRef));

        assertEquals(1, dropped("invalid_structure"));
        assertEquals(1, total());
        assertEquals(1, ignored());
    }

    @Test
    public void unchangedUpdateIsCountedAsNotUpdated() {
        VehicleActivityStructure activity = vehicle("v1");
        vehicleActivities.addAll(codespace, List.of(activity));
        awaitCached(1);

        vehicleActivities.addAll(codespace, List.of(activity));

        assertEquals(1, dropped("not_updated"));
        assertEquals(1, ignored());
    }

    @Test
    public void olderUpdateIsCountedAsOutdatedNotExpired() {
        ZonedDateTime now = ZonedDateTime.now();
        vehicleActivities.addAll(codespace, List.of(vehicle("v1", now, 59.91, 10.75)));
        awaitCached(1);

        vehicleActivities.addAll(codespace, List.of(vehicle("v1", now.minusMinutes(1), 59.92, 10.76)));

        assertEquals(1, dropped("outdated"));
        assertEquals(0, expired());
        assertEquals(1, ignored());
    }

    @Test
    public void expiredUpdateIsCountedAsExpired() {
        vehicleActivities.addAll(codespace, List.of(vehicle("v1", ZonedDateTime.now().minusDays(2), 59.91, 10.75)));

        assertEquals(1, dropped("expired"));
        assertEquals(1, expired());
    }

    @Test
    public void updateWithoutValidUntilIsCountedSeparately() {
        VehicleActivityStructure activity = vehicle("v1");
        activity.setValidUntilTime(null);

        vehicleActivities.addAll(codespace, List.of(activity));

        assertEquals(1, dropped("no_valid_until"));
        assertEquals(1, expired());
    }

    @Test
    public void storedUpdateAtAnInvalidLocationIsOnlyCountedAsSuccess() {
        vehicleActivities.addAll(codespace, List.of(vehicle("v1", ZonedDateTime.now(), 0, 0)));

        assertEquals(1, success());
        assertEquals(0, ignored());
    }

    @Test
    public void totalsAddUpForAMixedDelivery() {
        VehicleActivityStructure noVehicleRef = vehicle("v2");
        noVehicleRef.getMonitoredVehicleJourney().setVehicleRef(null);

        vehicleActivities.addAll(codespace, List.of(
                vehicle("v1"),
                noVehicleRef,
                vehicle("v3", ZonedDateTime.now().minusDays(2), 59.91, 10.75)));

        assertEquals(3, total());
        assertEquals(total(), success() + expired() + ignored());
    }

    @Test
    public void sameVehicleTwiceInOneDeliveryIsCountedAsDuplicate() {
        ZonedDateTime now = ZonedDateTime.now();

        vehicleActivities.addAll(codespace, List.of(
                vehicle("v1", now, 59.91, 10.75),
                vehicle("v1", now.plusSeconds(1), 59.92, 10.76)));

        assertEquals(1, success());
        assertEquals(1, dropped("duplicate"));
        assertEquals(total(), success() + expired() + ignored());
    }

    @Test
    public void sourceLatencyIsTimeFromReportedPositionUntilStored() {
        vehicleActivities.addAll(codespace, List.of(vehicle("v1", ZonedDateTime.now().minusSeconds(5), 59.91, 10.75)));

        Timer source = metrics.find("app.anshar.latency.source").tags("dataType", VM, "codespace", codespace).timer();
        assertNotNull(source, "source latency timer not registered");
        assertEquals(1, source.count());
        double millis = source.totalTime(TimeUnit.MILLISECONDS);
        assertTrue(millis >= 5_000 && millis < 15_000, "source latency was " + millis + " ms");
    }

    @Test
    public void vehicleWithoutReportedTimeIsCountedInsteadOfTimed() {
        VehicleActivityStructure activity = vehicle("v1");
        activity.setRecordedAtTime(null);
        activity.getMonitoredVehicleJourney().setLocationRecordedAtTime(null);

        vehicleActivities.addAll(codespace, List.of(activity));

        assertEquals(1, success());
        assertNull(metrics.find("app.anshar.latency.source").tags("dataType", VM, "codespace", codespace).timer(),
                "a vehicle without a reported time must not be timed");
        assertEquals(1, counter("app.anshar.latency.source.missing", "dataType", VM, "codespace", codespace));
    }
}
