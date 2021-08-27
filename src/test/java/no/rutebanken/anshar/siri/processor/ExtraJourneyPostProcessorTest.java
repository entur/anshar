package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.ExtraJourneyPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleModesEnumeration;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtraJourneyPostProcessorTest extends SpringBootBaseTest {
    ExtraJourneyPostProcessor processor = new ExtraJourneyPostProcessor("DUMMY");
    private String NSR_ID_OSLO_S = "NSR:StopPlace:337";
    private String NSR_ID_LILLESTROM= "NSR:StopPlace:451";

    @BeforeAll
    public static void init() {
        NetexUpdaterService.update("src/test/resources/RailStations.zip");
    }

    @Test
    public void testSaneSpeed() {
        final List<String> stopRefs = List.of(NSR_ID_OSLO_S, NSR_ID_LILLESTROM);
        final List<ZonedDateTime> arrivals_departures = List.of(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(20));
        Siri siri = createEt(stopRefs, arrivals_departures, arrivals_departures, true,
            VehicleModesEnumeration.RAIL
        );

        processor.process(siri);

        final List<EstimatedVehicleJourney> vehicleJourneies = siri
            .getServiceDelivery()
            .getEstimatedTimetableDeliveries()
            .get(0)
            .getEstimatedJourneyVersionFrames()
            .get(0)
            .getEstimatedVehicleJourneies();

        // Trip is valid, and should NOT be removed
        assertTrue(!vehicleJourneies.isEmpty());
    }

    @Test
    public void testRailMode() {
        final List<String> stopRefs = List.of(NSR_ID_OSLO_S, NSR_ID_LILLESTROM);
        final List<ZonedDateTime> arrivals_departures = List.of(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(20));
        Siri siri = createEt(stopRefs, arrivals_departures, arrivals_departures, true,
            VehicleModesEnumeration.RAIL
        );

        processor.process(siri);

        final List<EstimatedVehicleJourney> vehicleJourneies = siri
            .getServiceDelivery()
            .getEstimatedTimetableDeliveries()
            .get(0)
            .getEstimatedJourneyVersionFrames()
            .get(0)
            .getEstimatedVehicleJourneies();

        // Trip is valid, and should NOT be removed
        assertTrue(!vehicleJourneies.isEmpty());
    }

    @Test
    public void testTooFastSpeed() {
        final List<String> stopRefs = List.of(NSR_ID_OSLO_S, NSR_ID_LILLESTROM);
        final List<ZonedDateTime> arrivals_departures = List.of(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(3));
        Siri siri = createEt(stopRefs, arrivals_departures, arrivals_departures, true,
            VehicleModesEnumeration.RAIL
        );

        processor.process(siri);

        final List<EstimatedVehicleJourney> vehicleJourneies = siri
            .getServiceDelivery()
            .getEstimatedTimetableDeliveries()
            .get(0)
            .getEstimatedJourneyVersionFrames()
            .get(0)
            .getEstimatedVehicleJourneies();

        // Trip is invalid, and should be removed
        assertTrue(vehicleJourneies.isEmpty());
    }
    @Test
    public void testBusMode() {
        final List<String> stopRefs = List.of(NSR_ID_OSLO_S, NSR_ID_LILLESTROM);
        final List<ZonedDateTime> arrivals_departures = List.of(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(20));
        Siri siri = createEt(stopRefs, arrivals_departures, arrivals_departures, true,
            VehicleModesEnumeration.BUS
        );

        processor.process(siri);

        final List<EstimatedVehicleJourney> vehicleJourneies = siri
            .getServiceDelivery()
            .getEstimatedTimetableDeliveries()
            .get(0)
            .getEstimatedJourneyVersionFrames()
            .get(0)
            .getEstimatedVehicleJourneies();

        // Trip is invalid, and should be removed
        assertTrue(vehicleJourneies.isEmpty());
    }

    private Siri createEt(
        List<String> stopRefs, List<ZonedDateTime> arrivals, List<ZonedDateTime> departures,
        boolean isExtraJourney, VehicleModesEnumeration vehicleMode
    ) {
        if (stopRefs.size() < 2 && (stopRefs.size() != arrivals.size() || stopRefs.size() != departures.size())) {
            throw new RuntimeException("Parameter list-sizes must be more than two, and match ");
        }
        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        EstimatedVehicleJourney et = new EstimatedVehicleJourney();
        et.getVehicleModes().add(vehicleMode);
        et.setExtraJourney(isExtraJourney);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < stopRefs.size(); i++) {
            EstimatedCall call = new EstimatedCall();
            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue(stopRefs.get(i));
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(arrivals.get(i));
            call.setAimedDepartureTime(departures.get(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }
        et.setEstimatedCalls(estimatedCalls);
        etList.add(et);
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }
}
