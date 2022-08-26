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
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.ServiceFeatureRef;
import uk.org.siri.siri21.Siri;

import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.REMOVE_FREIGHT_TRAIN;

/**
 * Remove expired VehicleJourneys to avoid conflict in vehicleRef per date.
 *
 */
public class BaneNorRemoveFreightTrainPostProcessor extends ValueAdapter implements PostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BaneNorRemoveFreightTrainPostProcessor.class);

    // Indicates how long after latest arrival the data should be processed.
    private static final String FREIGHT_TRAIN_FEATURE_REF = "freightTrain";
    private String datasetId;

    public BaneNorRemoveFreightTrainPostProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    public void process(Siri siri) {

        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (etDeliveries != null) {
            for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                    int size = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();
                    estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().removeIf(et -> isFreightTrain(et.getServiceFeatureReves()));
                    if (estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size() != size) {
                        final int removedFreightTrains = size - estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();
                        logger.info("Removed {} freight trains", removedFreightTrains);
                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, REMOVE_FREIGHT_TRAIN, removedFreightTrains);
                    }
                }
            }
        }
    }

    private boolean isFreightTrain(List<ServiceFeatureRef> serviceFeatureReves) {
        for (ServiceFeatureRef ref : serviceFeatureReves) {
            if (ref != null && FREIGHT_TRAIN_FEATURE_REF.equals(ref.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
