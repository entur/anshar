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
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;

import java.math.BigInteger;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ADD_ORDER_TO_CALLS;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.OVERRIDE_EMPTY_DESTINATION_DISPLAY_FOR_EXTRA_JOURNEYS;

public class AddOrderToAllCallsPostProcessor extends ValueAdapter implements PostProcessor {

    private String datasetId;

    public AddOrderToAllCallsPostProcessor(String datasetId) {
        this.datasetId = datasetId;
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

                            int counter = 0;
                            final EstimatedVehicleJourney.RecordedCalls recordedCallsObj = estimatedVehicleJourney.getRecordedCalls();
                            if (recordedCallsObj != null) {
                                final List<RecordedCall> recordedCalls = recordedCallsObj.getRecordedCalls();
                                for (RecordedCall call : recordedCalls) {
                                    if (call.getOrder() == null) {
                                        counter++;
                                        if (call.getVisitNumber() != null) {
                                            call.setOrder(call.getVisitNumber());
                                        } else {
                                            call.setOrder(BigInteger.valueOf(counter));
                                        }
                                    }
                                }
                            }

                            final EstimatedVehicleJourney.EstimatedCalls estimatedCallsObj = estimatedVehicleJourney.getEstimatedCalls();
                            if (estimatedCallsObj != null) {
                                final List<EstimatedCall> estimatedCalls = estimatedCallsObj.getEstimatedCalls();
                                for (EstimatedCall call : estimatedCalls) {
                                    if (call.getOrder() == null) {
                                        counter++;
                                        if (call.getVisitNumber() != null) {
                                            call.setOrder(call.getVisitNumber());
                                        } else {
                                            call.setOrder(BigInteger.valueOf(counter));
                                        }
                                    }
                                }
                            }
                            if (counter > 0) {
                                getMetricsService().registerDataMapping(
                                    SiriDataType.ESTIMATED_TIMETABLE,
                                    datasetId,
                                    ADD_ORDER_TO_CALLS,
                                    counter
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}
