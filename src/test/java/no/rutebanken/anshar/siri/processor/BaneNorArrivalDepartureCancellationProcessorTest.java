/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.BaneNorArrivalDepartureCancellationProcessor;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class BaneNorArrivalDepartureCancellationProcessorTest {

    private final BaneNorArrivalDepartureCancellationProcessor processor = new BaneNorArrivalDepartureCancellationProcessor();

    @Test
    public void testCancellationOnStart() {
        boolean[] cancellations =                                   {true,         true,          false,          false};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{true, true}, {true, false}, {false, false}, {false, false}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }

    @Test
    public void testCancellationOnEnd() {
        boolean[] cancellations =                                   {false,          false,           false,         false,          true,         true,        true};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{false, false}, {false, false}, {false, false}, {false, false}, {false, true}, {true, true}, {true, true}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }


    @Test
    public void testCancellationOnMiddle() {
        boolean[] cancellations =                                   {false,          false,          true,          true,          false,          false,         false};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{false, false}, {false, false}, {false, true}, {true, false}, {false, false}, {false, false}, {false, false}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }


    @Test
    public void testMultipleCancellationOnMiddle() {
        boolean[] cancellations =                              {     false,          false,          true,         true,         false,           true,           true,           true,           false,       true,           true,         false,        false};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{false, false}, {false, false}, {false, true}, {true, false}, {false, false}, {false, true}, {true, true}, {true, false}, {false, false}, {false, true}, {true, false}, {false, false}, {false, false}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }

    @Test
    public void testCancellationOnSingleStop() {
        boolean[] cancellations =                                   {false,          false,         true,           false,         false,          false,         false};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{false, false}, {false, false}, {true, true}, {false, false}, {false, false}, {false, false}, {false, false}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }


    @Test
    public void testCancellationOnOnlyFirstStop() {
        boolean[] cancellations =                                   {true,        false,          false,          false,          false,           false,         false};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{true, true}, {true, false}, {false, false}, {false, false}, {false, false}, {false, false}, {false, false}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }

    @Test
    public void testCancellationOnOnlyLastStop() {
        boolean[] cancellations =                                   {false,          false,          false,           false,         false,          false,        true};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{false, false}, {false, false}, {false, false}, {false, false}, {false, false}, {false, true}, {true, true}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);


        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }

    @Test
    public void testCancellationOnStartAndEnd() {
        boolean[] cancellations =                                   {true,        true,          true,          false,          false,         false,          false,            true,         true,         true};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{true, true}, {true, true}, {true, false}, {false, false}, {false, false}, {false, false}, {false, false}, {false, true}, {true, true}, {true, true}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        Siri siri = createEstimatedVehicleJourney(cancellations);

        processor.process(siri);

        verifyCancellations(siri, new boolean[0], cancellations, expectedArrivalBoardingCancellationStatus);
    }

    @Test
    public void testCancellationOnStartAfterRecordedCalls() {
        boolean[] cancellations =                                   {true,        true,          true,           false,         false,          false,          false,           true,         true,         true};
        boolean[][] expectedArrivalBoardingCancellationStatus = {{true, true}, {true, true}, {true, false}, {false, false}, {false, false}, {false, false}, {false, false}, {false, true}, {true, true}, {true, true}};

        assertEquals("Wrong number of comparing patterns", cancellations.length, expectedArrivalBoardingCancellationStatus.length);

        int recordedCallsCount = 2;

        Siri siri = createEstimatedVehicleJourney(recordedCallsCount, cancellations);

        processor.process(siri);


        verifyCancellations(siri, Arrays.copyOfRange(cancellations, 0, recordedCallsCount), Arrays.copyOfRange(cancellations, recordedCallsCount, cancellations.length), expectedArrivalBoardingCancellationStatus);
    }

    private void verifyCancellations(Siri siri, boolean[] rcCancellations, boolean[] etCancellations, boolean[][] expectedArrivalBoardingCancellationStatus) {
        EstimatedTimetableDeliveryStructure timetableDeliveryStructure = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0);

        List<EstimatedVehicleJourney> estimatedVehicleJourneies = timetableDeliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies();
        EstimatedVehicleJourney estimatedVehicleJourney = estimatedVehicleJourneies.get(0);
        List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();

        List<RecordedCall> recordedCalls = new ArrayList<>();
        if (estimatedVehicleJourney.getRecordedCalls() != null && estimatedVehicleJourney.getRecordedCalls().getRecordedCalls() != null) {
            recordedCalls = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls();
        }

        assertEquals(rcCancellations.length, recordedCalls.size());
        assertEquals(etCancellations.length, estimatedCalls.size());

        int numberOfCalls = rcCancellations.length + etCancellations.length;
        assertEquals(numberOfCalls, expectedArrivalBoardingCancellationStatus.length);

        int i = rcCancellations.length;
        for (EstimatedCall estimatedCall : estimatedCalls) {

            boolean isArrivalCancelled = expectedArrivalBoardingCancellationStatus[i][0];
            boolean isDepartureCancelled = expectedArrivalBoardingCancellationStatus[i][1];

            assertEquals("EstimatedCall does not match cancellation (" + i + ")", isArrivalCancelled & isDepartureCancelled, estimatedCall.isCancellation() != null && estimatedCall.isCancellation());
            assertEquals("EstimatedCall does not match cancellation (" + i + ")", !isArrivalCancelled | !isDepartureCancelled, estimatedCall.isCancellation() == null | (estimatedCall.isCancellation() != null && !estimatedCall.isCancellation()));

            assertEquals("EstimatedCall does not match on Arrival (" + i + ")", isArrivalCancelled, (estimatedCall.getArrivalStatus().equals(CallStatusEnumeration.CANCELLED)));
            assertEquals("EstimatedCall does not match on Departure (" + i + ")", isDepartureCancelled, (estimatedCall.getDepartureStatus().equals(CallStatusEnumeration.CANCELLED)));
            i++;
        }

    }


    private Siri createEstimatedVehicleJourney(boolean... cancellationPattern) {
        return createEstimatedVehicleJourney(0, cancellationPattern);
    }
    private Siri createEstimatedVehicleJourney(int recordedCallsCount, boolean... cancellationPattern) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("NSB:Line:1");
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("2134");
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(true);


        EstimatedVehicleJourney.RecordedCalls recordedCalls = new EstimatedVehicleJourney.RecordedCalls();
        for (int i = 0; i < recordedCallsCount; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            RecordedCall call = new RecordedCall();
            call.setStopPointRef(stopPointRef);

            if (cancellationPattern[i]) {
                call.setCancellation(cancellationPattern[i]);
            }

            recordedCalls.getRecordedCalls().add(call);
        }
        if (recordedCallsCount > 0) {
            element.setRecordedCalls(recordedCalls);
        }


        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = recordedCallsCount; i < cancellationPattern.length; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);

            if (cancellationPattern[i]) {
                call.setCancellation(cancellationPattern[i]);
                call.setDepartureStatus(CallStatusEnumeration.CANCELLED);
                call.setArrivalStatus(CallStatusEnumeration.CANCELLED);
            } else {
                call.setDepartureStatus(CallStatusEnumeration.ON_TIME);
                call.setArrivalStatus(CallStatusEnumeration.ON_TIME);
            }

            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());


        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(Arrays.asList(element));
    }
}
