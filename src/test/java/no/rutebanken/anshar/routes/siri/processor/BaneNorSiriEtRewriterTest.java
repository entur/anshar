package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.processor.BaneNorSiriStopAssignmentPopulaterTest.unmarshallSiriFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class BaneNorSiriEtRewriterTest extends SpringBootBaseTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BaneNorSiriEtRewriter rewriter;

    @BeforeEach
    public void init() {
        rewriter = new BaneNorSiriEtRewriter("BNR");
    }

    @Disabled("Ignored because of excessive memory-usage which breaks CircleCI")
    @Test
    public void testMapping() throws Exception {
        logger.info("Reads routedata...");
        NetexUpdaterService.update("src/test/resources/rb_nsb-aggregated-netex.zip",
                "src/test/resources/rb_gjb-aggregated-netex.zip",
                "src/test/resources/rb_flt-aggregated-netex.zip",
                "src/test/resources/CurrentAndFuture_latest.zip");
//        NSBGtfsUpdaterService.update("src/test/resources/rb_nsb-aggregated-gtfs.zip",
//                "src/test/resources/rb_gjb-aggregated-gtfs.zip",
//                "src/test/resources/rb_flt-aggregated-gtfs.zip");

//        Siri siri = unmarshallSiriFile("src/test/resources/siri-et-gir-npe.xml");
        Siri siri = unmarshallSiriFile("src/test/resources/siri-et-from-bnr.xml");
        HashMap<String, List<String>> trainNumbersToStopsBefore = mapTrainNumbersToStops(siri);

        rewriter.process(siri);
        HashMap<String, List<String>> trainNumbersToStopsAfter = mapTrainNumbersToStops(siri);

        // Trainnumber 861 and 863 are NOT in NeTEx-data - they should NOT be included in trainNumbersToStopsAfter
        assertTrue(trainNumbersToStopsBefore.containsKey("861"));
        assertFalse(trainNumbersToStopsAfter.containsKey("861"));

        assertTrue(trainNumbersToStopsBefore.containsKey("863"));
        assertFalse(trainNumbersToStopsAfter.containsKey("863"));

        // Remove from trainNumbersToStopsBefore before content-comparison
        trainNumbersToStopsBefore.remove("861");
        trainNumbersToStopsBefore.remove("863");

        assertEquals(trainNumbersToStopsBefore.size(), trainNumbersToStopsAfter.size());
        for (Map.Entry<String, List<String>> before : trainNumbersToStopsBefore.entrySet()) {

            List<String> afterStops = trainNumbersToStopsAfter.get(before.getKey());
            List<String> beforeStops = before.getValue();
            if (afterStops.size() != beforeStops.size()) {
                logger.error("Trainnumber {} now has {} stops and not {} as before", before.getKey(), afterStops.size(), beforeStops.size());
            } else if (!afterStops.containsAll(beforeStops)){
                logger.error("The stops differ before and after:" +
                        "\n  Before: {}" +
                        "\n  After : {}", beforeStops, afterStops);
            }
        }

    }

    @Test
    public void testIgnoreUnknownDepartures() throws Exception {
        logger.info("Reads routedata...");
        NetexUpdaterService.update("src/test/resources/rb_nsb-aggregated-netex.zip",
                "src/test/resources/RailStations.zip");
        Siri siri = unmarshallSiriFile("src/test/resources/siri-et-from-bnr.xml");

        HashMap<String, List<String>> trainNumbersToStopsBefore = mapTrainNumbersToStops(siri);

        rewriter.process(siri);

        HashMap<String, List<String>> trainNumbersToStopsAfter = mapTrainNumbersToStops(siri);

        // Trainnumber 861 and 863 are NOT in NeTEx-data - they should NOT be included in trainNumbersToStopsAfter
        assertTrue(trainNumbersToStopsBefore.containsKey("861"));
        assertFalse(trainNumbersToStopsAfter.containsKey("861"));

        assertTrue(trainNumbersToStopsBefore.containsKey("863"));
        assertFalse(trainNumbersToStopsAfter.containsKey("863"));

    }

    @Test
    public void testIsMatch() {
        StopTime stopTime = new StopTime("NSR:STOP:1234", 1, 3600, 3660);
        ZonedDateTime arrivalTime = ZonedDateTime.now()
                .with(ChronoField.HOUR_OF_DAY, 0)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_DAY, 3600);
        ZonedDateTime departureTime = ZonedDateTime.now()
                .with(ChronoField.HOUR_OF_DAY, 0)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_DAY, 3660);

        assertTrue(rewriter.isMatch(false, stopTime, "NSR:STOP:1234", arrivalTime, departureTime));
    }

    @Test
    public void testIsMatchForTripPassingMidnight() {
        StopTime stopTime = new StopTime("NSR:STOP:1234", 1, 3600 + 86400, 3660+86400);
        ZonedDateTime arrivalTime = ZonedDateTime.now()
                .with(ChronoField.HOUR_OF_DAY, 0)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_DAY, 3600);
        ZonedDateTime departureTime = ZonedDateTime.now()
                .with(ChronoField.HOUR_OF_DAY, 0)
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_DAY, 3660);

        assertTrue(rewriter.isMatch(false, stopTime, "NSR:STOP:1234", arrivalTime, departureTime));
    }

    private HashMap<String, List<String>> mapTrainNumbersToStops(Siri siri) {
        HashMap<String, List<String>> result = new HashMap<>();
        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (etDeliveries != null) {
            for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                    List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                    for (EstimatedVehicleJourney journey : estimatedVehicleJourneies) {
                        String trainNumber = journey.getVehicleRef().getValue();
                        List<String> recorded = journey.getRecordedCalls() == null ? Collections.emptyList() : journey.getRecordedCalls().getRecordedCalls().stream().map(c -> c.getStopPointRef().getValue()).collect(Collectors.toList());
                        List<String> estimated = journey.getEstimatedCalls() == null ? Collections.emptyList() : journey.getEstimatedCalls().getEstimatedCalls().stream().map(c -> c.getStopPointRef().getValue()).collect(Collectors.toList());
                        ArrayList<String> stops = new ArrayList<>();
                        stops.addAll(recorded);
                        stops.addAll(estimated);
                        result.put(trainNumber, stops);
                    }
                }
            }
        }
        return result;
    }
}