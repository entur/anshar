package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.ExtraJourneyPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtraJourneyPostProcessorTest extends SpringBootBaseTest {
    ExtraJourneyPostProcessor processor = new ExtraJourneyPostProcessor("DUMMY");
    private String NSR_ID_OSLO_S = "NSR:StopPlace:337";
    private String NSR_ID_LILLESTROM= "NSR:StopPlace:451";


    private String NSR_ID_PORSGRUNN = "NSR:StopPlace:12";
    private String NSR_ID_TORP = "NSR:StopPlace:672";
    private String NSR_ID_STOKKE = "NSR:StopPlace:132";

    @BeforeAll
    public static void init() {
        NetexUpdaterService.update("src/test/resources/RailStations.zip");
    }

    @Test
    public void testSaneSpeed() {
        final List<String> stopRefs = List.of(NSR_ID_OSLO_S, NSR_ID_LILLESTROM);
        final List<ZonedDateTime> arrivals_departures = List.of(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(20));
        Siri siri = createEt(stopRefs, arrivals_departures, true,
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
        Siri siri = createEt(stopRefs, arrivals_departures, true,
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
        Siri siri = createEt(stopRefs, arrivals_departures, true,
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
    public void testExtraJourneyWithRecordedCallOnlyAlighting() {
        final List<String> stopRefs = List.of(NSR_ID_PORSGRUNN, NSR_ID_TORP, NSR_ID_STOKKE);
        final List<ZonedDateTime> arrivals_departures = List.of(
                ZonedDateTime.now(),
                ZonedDateTime.now().plusMinutes(46),
                ZonedDateTime.now().plusMinutes(47)
        );
        List<Pair<ArrivalBoardingActivityEnumeration, DepartureBoardingActivityEnumeration>> alighting_boarding = List.of(
                Pair.of(ArrivalBoardingActivityEnumeration.NO_ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.NO_BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.NO_BOARDING)
        );
        Siri siri = createEt(
                stopRefs,
                arrivals_departures,
                alighting_boarding,
                VehicleModesEnumeration.RAIL,
                1);

        processor.process(siri);

        List<EstimatedVehicleJourney> vehicleJourneies = siri
                .getServiceDelivery()
                .getEstimatedTimetableDeliveries()
                .get(0)
                .getEstimatedJourneyVersionFrames()
                .get(0)
                .getEstimatedVehicleJourneies();

        // Trip is valid, and should NOT be removed
        assertFalse(vehicleJourneies.isEmpty());

        // Switch to boarding, and it should fail
        alighting_boarding = List.of(
                Pair.of(ArrivalBoardingActivityEnumeration.NO_ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING)
        );
        siri = createEt(
                stopRefs,
                arrivals_departures,
                alighting_boarding,
                VehicleModesEnumeration.RAIL,
                1);

        processor.process(siri);

        vehicleJourneies = siri
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
    public void testExtraJourneyWithOnlyAlighting() {
        final List<String> stopRefs = List.of(NSR_ID_PORSGRUNN, NSR_ID_TORP, NSR_ID_STOKKE);
        final List<ZonedDateTime> arrivals_departures = List.of(
                ZonedDateTime.now(),
                ZonedDateTime.now().plusMinutes(46),
                ZonedDateTime.now().plusMinutes(47)
        );
        List<Pair<ArrivalBoardingActivityEnumeration, DepartureBoardingActivityEnumeration>> alighting_boarding = List.of(
                Pair.of(ArrivalBoardingActivityEnumeration.NO_ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.NO_BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.NO_BOARDING)
        );
        Siri siri = createEt(
                stopRefs,
                arrivals_departures,
                alighting_boarding,
                VehicleModesEnumeration.RAIL,
                0);

        processor.process(siri);

        List<EstimatedVehicleJourney> vehicleJourneies = siri
                .getServiceDelivery()
                .getEstimatedTimetableDeliveries()
                .get(0)
                .getEstimatedJourneyVersionFrames()
                .get(0)
                .getEstimatedVehicleJourneies();

        // Trip is valid, and should NOT be removed
        assertFalse(vehicleJourneies.isEmpty());

        // Switch to boarding, and it should fail
        alighting_boarding = List.of(
                Pair.of(ArrivalBoardingActivityEnumeration.NO_ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING),
                Pair.of(ArrivalBoardingActivityEnumeration.ALIGHTING, DepartureBoardingActivityEnumeration.BOARDING)
        );
        siri = createEt(
                stopRefs,
                arrivals_departures,
                alighting_boarding,
                VehicleModesEnumeration.RAIL,
                0);

        processor.process(siri);

        vehicleJourneies = siri
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
        Siri siri = createEt(stopRefs, arrivals_departures, true,
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
        List<String> stopRefs, List<ZonedDateTime> arrivals_departures,
        boolean isExtraJourney, VehicleModesEnumeration vehicleMode
    ) {
        if (stopRefs.size() < 2 && (stopRefs.size() != arrivals_departures.size())) {
            throw new RuntimeException("Parameter list-sizes must be more than two, and match ");
        }
        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        EstimatedVehicleJourney et = new EstimatedVehicleJourney();
        et.getVehicleModes().add(vehicleMode);
        et.setExtraJourney(isExtraJourney);
        if (isExtraJourney) {
            et.setEstimatedVehicleJourneyCode("TST:ServiceJourney:" + System.currentTimeMillis());
        }

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < stopRefs.size(); i++) {
            EstimatedCall call = new EstimatedCall();
            StopPointRefStructure stopPointRef = new StopPointRefStructure();
            stopPointRef.setValue(stopRefs.get(i));
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(arrivals_departures.get(i));
            call.setAimedDepartureTime(arrivals_departures.get(i));

            if (i == 0) {
                call.setArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.NO_ALIGHTING);
                call.setDepartureBoardingActivity(DepartureBoardingActivityEnumeration.BOARDING);
            } else {
                call.setArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.ALIGHTING);
                call.setDepartureBoardingActivity(DepartureBoardingActivityEnumeration.NO_BOARDING);
            }

            estimatedCalls.getEstimatedCalls().add(call);
        }
        et.setEstimatedCalls(estimatedCalls);
        etList.add(et);
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }

    private Siri createEt(
        List<String> stopRefs, List<ZonedDateTime> arrivals_departures,
        List<Pair<ArrivalBoardingActivityEnumeration,DepartureBoardingActivityEnumeration>> alighting_boarding,
        VehicleModesEnumeration vehicleMode,
        int numberOfRecordedCalls) {
        if (stopRefs.size() < 2 && (
                stopRefs.size() != arrivals_departures.size()) ||
                stopRefs.size() != alighting_boarding.size()) {
            throw new RuntimeException("Parameter list-sizes must be more than two, and match ");
        }
        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        EstimatedVehicleJourney et = new EstimatedVehicleJourney();
        et.getVehicleModes().add(vehicleMode);
        et.setExtraJourney(true);
        et.setEstimatedVehicleJourneyCode("TST:ServiceJourney:" + System.currentTimeMillis());

        EstimatedVehicleJourney.RecordedCalls recordedCalls = new EstimatedVehicleJourney.RecordedCalls();
        for (int i = 0; i < numberOfRecordedCalls; i++) {
            RecordedCall call = new RecordedCall();
            StopPointRefStructure stopPointRef = new StopPointRefStructure();
            stopPointRef.setValue(stopRefs.get(i));
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(arrivals_departures.get(i));
            call.setAimedDepartureTime(arrivals_departures.get(i));

            call.setArrivalBoardingActivity(alighting_boarding.get(i).getLeft());
            call.setDepartureBoardingActivity(alighting_boarding.get(i).getRight());

            recordedCalls.getRecordedCalls().add(call);
        }
        et.setRecordedCalls(recordedCalls);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = recordedCalls.getRecordedCalls().size(); i < stopRefs.size(); i++) {
            EstimatedCall call = new EstimatedCall();
            StopPointRefStructure stopPointRef = new StopPointRefStructure();
            stopPointRef.setValue(stopRefs.get(i));
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(arrivals_departures.get(i));
            call.setAimedDepartureTime(arrivals_departures.get(i));

            call.setArrivalBoardingActivity(alighting_boarding.get(i).getLeft());
            call.setDepartureBoardingActivity(alighting_boarding.get(i).getRight());

            estimatedCalls.getEstimatedCalls().add(call);
        }
        et.setEstimatedCalls(estimatedCalls);
        etList.add(et);
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }
}
