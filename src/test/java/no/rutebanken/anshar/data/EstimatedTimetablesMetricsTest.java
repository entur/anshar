package no.rutebanken.anshar.data;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ET element in a delivery is accounted for exactly once: stored (success), expired or
 * ignored, and every element not stored is counted in app.anshar.data.dropped with its reason.
 * Each test uses its own codespace, since the metrics registry is shared by the Spring context.
 */
public class EstimatedTimetablesMetricsTest extends SpringBootBaseTest {

    private static final String ET = "ESTIMATED_TIMETABLE";

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private PrometheusMetricsService metrics;

    private String codespace;

    @BeforeEach
    public void init() {
        estimatedTimetables.clearAll();
        codespace = "E" + UUID.randomUUID().toString().substring(0, 8);
    }

    private double counter(String name, String... tags) {
        Counter counter = metrics.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private double dropped(String reason) {
        return counter("app.anshar.data.dropped", "dataType", ET, "codespace", codespace, "reason", reason);
    }

    private double total() {
        return counter("app.anshar.data.total", "dataType", ET, "agency", codespace);
    }

    private double success() {
        return counter("app.anshar.data.success", "dataType", ET, "agency", codespace);
    }

    private double expired() {
        return counter("app.anshar.data.expired", "dataType", ET, "agency", codespace);
    }

    private double ignored() {
        return counter("app.anshar.data.ignored", "dataType", ET, "agency", codespace);
    }

    private static EstimatedVehicleJourney journey(String vehicleRef, ZonedDateTime recordedAt, ZonedDateTime arrival) {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        journey.setLineRef(lineRef);
        VehicleRef ref = new VehicleRef();
        ref.setValue(vehicleRef);
        journey.setVehicleRef(ref);
        journey.setIsCompleteStopSequence(true);

        EstimatedVehicleJourney.EstimatedCalls calls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < 3; i++) {
            StopPointRefStructure stop = new StopPointRefStructure();
            stop.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stop);
            call.setAimedArrivalTime(arrival);
            call.setExpectedArrivalTime(arrival);
            call.setAimedDepartureTime(arrival);
            call.setExpectedDepartureTime(arrival);
            call.setOrder(BigInteger.valueOf(i));
            call.setVisitNumber(BigInteger.valueOf(i));
            calls.getEstimatedCalls().add(call);
        }
        journey.setEstimatedCalls(calls);
        journey.setRecordedAtTime(recordedAt);
        return journey;
    }

    private static EstimatedVehicleJourney journey(String vehicleRef) {
        return journey(vehicleRef, ZonedDateTime.now(), ZonedDateTime.now().plusHours(1));
    }

    @Test
    public void journeyTooFarAheadIsCountedAsDropped() {
        estimatedTimetables.addAll(codespace, List.of(journey("v1", ZonedDateTime.now(), ZonedDateTime.now().plusDays(60))));

        assertEquals(1, dropped("too_far_ahead"));
        assertEquals(1, ignored());
    }

    @Test
    public void olderJourneyIsCountedAsOutdated() {
        ZonedDateTime now = ZonedDateTime.now();
        estimatedTimetables.addAll(codespace, List.of(journey("v1", now, now.plusHours(1))));

        estimatedTimetables.addAll(codespace, List.of(journey("v1", now.minusMinutes(1), now.plusHours(2))));

        assertEquals(1, dropped("outdated"));
        assertEquals(total(), success() + expired() + ignored());
    }

    @Test
    public void expiredJourneyIsCountedAsExpired() {
        estimatedTimetables.addAll(codespace, List.of(journey("v1", ZonedDateTime.now(), ZonedDateTime.now().minusDays(2))));

        assertEquals(1, dropped("expired"));
        assertEquals(1, expired());
    }

    @Test
    public void sameJourneyTwiceInOneDeliveryIsCountedAsDuplicate() {
        ZonedDateTime now = ZonedDateTime.now();

        estimatedTimetables.addAll(codespace, List.of(
                journey("v1", now, now.plusHours(1)),
                journey("v1", now, now.plusHours(2))));

        assertEquals(1, success());
        assertEquals(1, dropped("duplicate"));
        assertEquals(total(), success() + expired() + ignored());
    }

    @Test
    public void sourceLatencyIsTimeFromRecordedAtUntilStored() {
        estimatedTimetables.addAll(codespace, List.of(journey("v1", ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now().plusHours(1))));

        Timer source = metrics.find("app.anshar.latency.source").tags("dataType", ET, "codespace", codespace).timer();
        assertNotNull(source, "source latency timer not registered");
        assertEquals(1, source.count());
        double millis = source.totalTime(TimeUnit.MILLISECONDS);
        assertTrue(millis >= 5_000 && millis < 15_000, "source latency was " + millis + " ms");
    }

    @Test
    public void journeyWithoutRecordedAtIsCountedInsteadOfTimed() {
        EstimatedVehicleJourney journey = journey("v1");
        journey.setRecordedAtTime(null);

        estimatedTimetables.addAll(codespace, List.of(journey));

        assertNull(metrics.find("app.anshar.latency.source").tags("dataType", ET, "codespace", codespace).timer(),
                "a missing RecordedAtTime must not be timed as zero latency");
        assertEquals(1, counter("app.anshar.latency.source.missing", "dataType", ET, "codespace", codespace));
    }
}
