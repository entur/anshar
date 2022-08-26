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
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.Siri;

import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.OVERRIDE_EMPTY_DESTINATION_DISPLAY_FOR_EXTRA_JOURNEYS;

public class ExtraJourneyDestinationDisplayPostProcessor extends ValueAdapter implements PostProcessor {

    private final NaturalLanguageStringStructure emptyDestinationDisplay;
    public static final String DUMMY_DESTINATION_DISPLAY = "-";
    private String datasetId;

    public ExtraJourneyDestinationDisplayPostProcessor(String datasetId) {
        this.datasetId = datasetId;

        emptyDestinationDisplay = new NaturalLanguageStringStructure();
        emptyDestinationDisplay.setValue(DUMMY_DESTINATION_DISPLAY);
    }

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
                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            if (estimatedVehicleJourney.isExtraJourney() != null && estimatedVehicleJourney.isExtraJourney()) {
                                final EstimatedVehicleJourney.EstimatedCalls estimatedCallsObj = estimatedVehicleJourney
                                    .getEstimatedCalls();
                                if (estimatedCallsObj != null) {
                                    final List<EstimatedCall> estimatedCalls = estimatedCallsObj.getEstimatedCalls();
                                    int counter = 0;
                                    for (EstimatedCall estimatedCall : estimatedCalls) {
                                        if (estimatedCall.getDestinationDisplaies().isEmpty()) {
                                            estimatedCall.getDestinationDisplaies().add(emptyDestinationDisplay);
                                            counter++;
                                        } else {
                                            final NaturalLanguageStringStructure destDisplay = estimatedCall.getDestinationDisplaies().get(0);
                                            if (destDisplay.getValue().isBlank()) {
                                                destDisplay.setValue(emptyDestinationDisplay.getValue());
                                                counter++;
                                            }
                                        }
                                    }

                                    if (counter > 0) {
                                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, OVERRIDE_EMPTY_DESTINATION_DISPLAY_FOR_EXTRA_JOURNEYS, counter);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
