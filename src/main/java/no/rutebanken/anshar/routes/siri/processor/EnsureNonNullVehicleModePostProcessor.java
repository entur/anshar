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
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;

import java.util.List;

public class EnsureNonNullVehicleModePostProcessor extends ValueAdapter implements PostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(
        EnsureNonNullVehicleModePostProcessor.class);

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame
                            .getEstimatedVehicleJourneies();
                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            if (estimatedVehicleJourney.getVehicleModes() != null) {
                                if (!estimatedVehicleJourney.getVehicleModes().isEmpty() &&
                                    estimatedVehicleJourney.getVehicleModes().get(0) == null) {
                                    logger.warn("Clearing VehicleMode");
                                    estimatedVehicleJourney.getVehicleModes().clear();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
