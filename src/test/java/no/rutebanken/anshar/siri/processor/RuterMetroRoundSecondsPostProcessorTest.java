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
import no.rutebanken.anshar.routes.siri.processor.RuterMetroRoundSecondsPostProcessor;
import org.junit.Test;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class RuterMetroRoundSecondsPostProcessorTest {

    RuterMetroRoundSecondsPostProcessor processor = new RuterMetroRoundSecondsPostProcessor();

    @Test
    public void tesAimedTimesRoundDown() {
        EstimatedVehicleJourney et = createEstimatedVehicleJourney();

        ZonedDateTime time = ZonedDateTime.of(2018, 11, 11, 2, 2, 00, 0, ZoneId.systemDefault());

        et.getRecordedCalls().getRecordedCalls().get(0).setAimedArrivalTime(time);
        et.getRecordedCalls().getRecordedCalls().get(0).setAimedDepartureTime(time.plusSeconds(5));

        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedArrivalTime(time.plusSeconds(35));
        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedDepartureTime(time.plusSeconds(44));


        Siri siri = new SiriObjectFactory(Instant.now()).createETServiceDelivery(Arrays.asList(et));

        // Assert different times are set
        assertEquals(time, et.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusSeconds(5), et.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime());
        assertEquals(time.plusSeconds(35), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusSeconds(44), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime());

        processor.process(siri);

        // Assert that all have been rounded down
        assertEquals(time, et.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime());
        assertEquals(time, et.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime());
        assertEquals(time, et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime());
        assertEquals(time, et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime());
    }


    @Test
    public void testAimedTimesRoundUp() {
        EstimatedVehicleJourney et = createEstimatedVehicleJourney();

        ZonedDateTime time = ZonedDateTime.of(2018, 11, 11, 2, 2, 00, 0, ZoneId.systemDefault());

        et.getRecordedCalls().getRecordedCalls().get(0).setAimedArrivalTime(time.plusSeconds(45));
        et.getRecordedCalls().getRecordedCalls().get(0).setAimedDepartureTime(time.plusSeconds(46));

        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedArrivalTime(time.plusSeconds(45).plusNanos(1));
        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedDepartureTime(time.plusSeconds(59));


        Siri siri = new SiriObjectFactory(Instant.now()).createETServiceDelivery(Arrays.asList(et));

        // Assert different times are set
        assertEquals(time.plusSeconds(45), et.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusSeconds(46), et.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime());
        assertEquals(time.plusSeconds(45).plusNanos(1), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusSeconds(59), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime());

        processor.process(siri);

        // Assert that last 3 have been rounded up
        assertEquals(time, et.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusMinutes(1), et.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime());
        assertEquals(time.plusMinutes(1), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusMinutes(1), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime());
    }
    @Test
    public void testMultipleAimedTimes() {
        EstimatedVehicleJourney et = createEstimatedVehicleJourney();

        ZonedDateTime time = ZonedDateTime.of(2018, 11, 11, 2, 2, 00, 0, ZoneId.systemDefault());

        et.getRecordedCalls().getRecordedCalls().get(0).setAimedArrivalTime(null);
        et.getRecordedCalls().getRecordedCalls().get(0).setAimedDepartureTime(time.plusSeconds(15));

        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedArrivalTime(time.plusSeconds(30));
        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedDepartureTime(time.plusSeconds(45));


        Siri siri = new SiriObjectFactory(Instant.now()).createETServiceDelivery(Arrays.asList(et));

        // Assert different times are set
        assertNull(et.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusSeconds(15), et.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime());
        assertEquals(time.plusSeconds(30), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime());
        assertEquals(time.plusSeconds(45), et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime());

        processor.process(siri);

        assertNull(et.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime());
        assertEquals(time, et.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime());
        assertEquals(time, et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime());
        assertEquals(time, et.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime());
    }

    @Test
    public void testNullAimedTimes() {
        EstimatedVehicleJourney et = createEstimatedVehicleJourney();

        et.getRecordedCalls().getRecordedCalls().get(0).setAimedArrivalTime(null);
        et.getRecordedCalls().getRecordedCalls().get(0).setAimedDepartureTime(null);

        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedArrivalTime(null);
        et.getEstimatedCalls().getEstimatedCalls().get(0).setAimedDepartureTime(null);

        Siri siri = new SiriObjectFactory(Instant.now()).createETServiceDelivery(Arrays.asList(et));

        processor.process(siri);
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney() {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();

        EstimatedVehicleJourney.RecordedCalls recordedCalls = new EstimatedVehicleJourney.RecordedCalls();
        recordedCalls.getRecordedCalls().add(new RecordedCall());

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        estimatedCalls.getEstimatedCalls().add(new EstimatedCall());

        element.setRecordedCalls(recordedCalls);
        element.setEstimatedCalls(estimatedCalls);

        return element;
    }
}
