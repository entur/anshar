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

package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.Siri;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaneNorRemoveExpiredJourneysPostProcessorTest extends SpringBootBaseTest {
    SiriObjectFactory  objFactory = new SiriObjectFactory(Instant.now());

    BaneNorRemoveExpiredJourneysPostProcessor processor = new BaneNorRemoveExpiredJourneysPostProcessor("BNR");

    @Test
    public void testFutureJourneysAreNotRemoved() {
        List et = new ArrayList();
        et.add(createEstimatedVehicleJourney(3, ZonedDateTime.now().plusMinutes(30)));

        Siri siri = objFactory.createETServiceDelivery(et);
        processor.process(siri);
        assertEquals(1, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size());
    }

    @Test
    public void testExpiredJourneysAreRemoved() {
        List et = new ArrayList();
        et.add(createEstimatedVehicleJourney(10, ZonedDateTime.now().minusMinutes(30)));

        Siri siri = objFactory.createETServiceDelivery(et);
        processor.process(siri);
        assertEquals(0, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size());
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(int callCount, ZonedDateTime time) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        element.setIsCompleteStopSequence(true);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < callCount; i++) {

            EstimatedCall call = new EstimatedCall();
            if (i > 0) {
                call.setAimedArrivalTime(time);
                call.setExpectedArrivalTime(time);
            }
            if (i < (callCount-1)) {
                call.setAimedDepartureTime(time);
                call.setExpectedDepartureTime(time);
            }

            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
