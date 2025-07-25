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

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import java.util.List;

public class RemoveDetailedAPCDataPostProcessor extends ValueAdapter implements PostProcessor {
    private Logger logger = LoggerFactory.getLogger(RemoveDetailedAPCDataPostProcessor.class);

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri != null && siri.getServiceDelivery() != null) {

            List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            for (EstimatedTimetableDeliveryStructure delivery : estimatedTimetableDeliveries) {
                List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = delivery.getEstimatedJourneyVersionFrames();
                for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                    List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                    for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                        if (estimatedVehicleJourney.getRecordedCalls() != null) {
                            List<RecordedCall> calls = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls();
                            for (RecordedCall call : calls) {
                                call.getRecordedDepartureOccupancies().clear();
                                call.getRecordedDepartureCapacities().clear();
                            }
                        }

                        if (estimatedVehicleJourney.getEstimatedCalls() != null) {
                            List<EstimatedCall> calls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
                            for (EstimatedCall call : calls) {
                                call.getRecordedDepartureOccupancies().clear();
                                call.getRecordedDepartureCapacities().clear();
                                call.getExpectedDepartureOccupancies().clear();
                                call.getExpectedDepartureCapacities().clear();
                            }
                        }
                    }
                }
            }
        }
    }
}
