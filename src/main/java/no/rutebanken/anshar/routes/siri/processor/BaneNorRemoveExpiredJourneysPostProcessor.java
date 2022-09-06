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
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static no.rutebanken.anshar.data.EstimatedTimetables.getLatestArrivalTime;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.REMOVE_EXPIRED_JOURNEYS;

/**
 * Remove expired VehicleJourneys to avoid conflict in vehicleRef per date.
 *
 */
public class BaneNorRemoveExpiredJourneysPostProcessor extends ValueAdapter implements PostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BaneNorRemoveExpiredJourneysPostProcessor.class);

    // Indicates how long after latest arrival the data should be processed.
    private static final long FILTER_LIMIT_MINUTES = 10;
    private String datasetId;

    public BaneNorRemoveExpiredJourneysPostProcessor(String datasetId) {
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
                    estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().removeIf(this::isExpired);
                    if (estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size() != size) {
                        final int removedJourneys = size - estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();
                        logger.info("Removed {} expired journeys", removedJourneys);
                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, REMOVE_EXPIRED_JOURNEYS, removedJourneys);
                    }
                }
            }
        }
    }

    private boolean isExpired(EstimatedVehicleJourney vehicleJourney) {
        ZonedDateTime expiryTimestamp = getLatestArrivalTime(vehicleJourney);

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp.plus(FILTER_LIMIT_MINUTES, ChronoUnit.MINUTES), ChronoUnit.MILLIS) < 0;
        } else {
            return true;
        }
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
