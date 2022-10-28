package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.EnsureIncreasingTimesProcessor;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import javax.xml.bind.JAXBException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EnsureIncreasingTimesProcessorTest extends SpringBootBaseTest {

    private EnsureIncreasingTimesProcessor processor = new EnsureIncreasingTimesProcessor("TST");

    @Test
    public void testValidData() throws JAXBException {
        Siri siri = createEt(ZonedDateTime.parse("2022-10-28T13:09:00+02:00"), 0, 0, 3);

        String before = SiriXml.toXml(siri);

        processor.process(siri);

        String after = SiriXml.toXml(siri);

        assertEquals(before, after);
    }


    @Test
    public void testNonIncreasingTimesData() throws JAXBException {
        Siri siri = createEt(ZonedDateTime.parse("2022-10-28T13:09:00+02:00"), 120, 0, 3);

        /*
            Remove updated times for second EstimatedCall
            +setPredictionInaccurate
         */
        EstimatedCall secondCall = siri.getServiceDelivery()
                .getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().get(0)
                .getEstimatedCalls().getEstimatedCalls().get(1);
        secondCall.setExpectedArrivalTime(secondCall.getAimedArrivalTime());
        secondCall.setExpectedDepartureTime(secondCall.getAimedDepartureTime());

        String before = SiriXml.toXml(siri);

        assertEquals(secondCall.getExpectedArrivalTime(), secondCall.getAimedArrivalTime());
        assertEquals(secondCall.getExpectedDepartureTime(), secondCall.getAimedDepartureTime());

        processor.process(siri);

        String after = SiriXml.toXml(siri);

        assertNotEquals(before, after);

        assertNotEquals(secondCall.getExpectedArrivalTime(), secondCall.getAimedArrivalTime());
        assertNotEquals(secondCall.getExpectedDepartureTime(), secondCall.getAimedDepartureTime());

    }


    @Test
    public void testNoExpectedTimesData() throws JAXBException {
        Siri siri = createEt(ZonedDateTime.parse("2022-10-28T13:09:00+02:00"), 120, 3, 3);

        /*
            Remove updated times for second RecordedCall
            +setPredictionInaccurate
         */
        RecordedCall secondRecordedCall = siri.getServiceDelivery()
                .getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().get(0)
                .getRecordedCalls().getRecordedCalls().get(1);

        secondRecordedCall.setExpectedArrivalTime(null);
        secondRecordedCall.setActualArrivalTime(null);
        secondRecordedCall.setExpectedDepartureTime(null);
        secondRecordedCall.setActualDepartureTime(null);
        secondRecordedCall.setPredictionInaccurate(true);

        assertNull(secondRecordedCall.getExpectedArrivalTime());
        assertNull(secondRecordedCall.getExpectedDepartureTime());
        assertNull(secondRecordedCall.getActualArrivalTime());
        assertNull(secondRecordedCall.getActualDepartureTime());

        /*
            Remove updated times for second EstimatedCall
            +setPredictionInaccurate
         */
        EstimatedCall secondCall = siri.getServiceDelivery()
                .getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().get(0)
                .getEstimatedCalls().getEstimatedCalls().get(1);

        secondCall.setExpectedArrivalTime(null);
        secondCall.setExpectedDepartureTime(null);
        secondCall.setPredictionInaccurate(true);

        String before = SiriXml.toXml(siri);

        assertNull(secondCall.getExpectedArrivalTime());
        assertNull(secondCall.getExpectedDepartureTime());

        processor.process(siri);

        String after = SiriXml.toXml(siri);

        assertNotEquals(before, after);

        assertNotNull(secondRecordedCall.getExpectedArrivalTime());
        assertNotNull(secondRecordedCall.getExpectedDepartureTime());

        // Actual times should not be altered
        assertNull(secondRecordedCall.getActualArrivalTime());
        assertNull(secondRecordedCall.getActualDepartureTime());


        assertNotNull(secondCall.getExpectedArrivalTime());
        assertNotNull(secondCall.getExpectedDepartureTime());

        assertNotEquals(secondCall.getExpectedArrivalTime(), secondCall.getAimedArrivalTime());
        assertNotEquals(secondCall.getExpectedDepartureTime(), secondCall.getAimedDepartureTime());

    }

    private Siri createEt(ZonedDateTime aimedStartTime, int delaySeconds, int recordedCallCount, int estimatedCallCount) {
        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        EstimatedVehicleJourney et = new EstimatedVehicleJourney();

        EstimatedVehicleJourney.RecordedCalls recordedCalls = new EstimatedVehicleJourney.RecordedCalls();
        for (int i = 0; i < recordedCallCount; i++) {
            RecordedCall call = new RecordedCall();

            ZonedDateTime currentAimedTime = aimedStartTime.plusMinutes(i);
            ZonedDateTime currentActualTime = currentAimedTime.plusSeconds(delaySeconds);

            call.setAimedArrivalTime(currentAimedTime);
            call.setActualArrivalTime(currentActualTime);

            call.setAimedDepartureTime(currentAimedTime);
            call.setActualDepartureTime(currentActualTime);

            recordedCalls.getRecordedCalls().add(call);
        }
        if (recordedCalls.getRecordedCalls() != null && !recordedCalls.getRecordedCalls().isEmpty()) {
            et.setRecordedCalls(recordedCalls);
        }

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < estimatedCallCount; i++) {
            EstimatedCall call = new EstimatedCall();

            ZonedDateTime currentAimedTime = aimedStartTime.plusMinutes(recordedCallCount + i);
            ZonedDateTime currentExpectedTime = currentAimedTime.plusSeconds(delaySeconds);

            call.setAimedArrivalTime(currentAimedTime);
            call.setExpectedArrivalTime(currentExpectedTime);

            call.setAimedDepartureTime(currentAimedTime);
            call.setExpectedDepartureTime(currentExpectedTime);
            estimatedCalls.getEstimatedCalls().add(call);
        }
        et.setEstimatedCalls(estimatedCalls);

        etList.add(et);
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }
}
