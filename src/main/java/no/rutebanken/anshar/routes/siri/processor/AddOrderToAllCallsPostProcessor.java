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
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import java.math.BigInteger;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ADD_ORDER_TO_CALLS;

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

                            int updatedOrderIndex = 0;
                            int ruleAppliedCounter = 0;
                            final EstimatedVehicleJourney.RecordedCalls recordedCallsObj = estimatedVehicleJourney.getRecordedCalls();
                            if (recordedCallsObj != null) {
                                final List<RecordedCall> recordedCalls = recordedCallsObj.getRecordedCalls();
                                for (RecordedCall call : recordedCalls) {
                                    if (call.getOrder() == null) {
                                        updatedOrderIndex++;
                                        if (call.getVisitNumber() != null) {
                                            call.setOrder(call.getVisitNumber());
                                            updatedOrderIndex = call.getVisitNumber().intValue();
                                            ruleAppliedCounter++;
                                        } else {
                                            call.setOrder(BigInteger.valueOf(updatedOrderIndex));
                                            ruleAppliedCounter++;
                                        }
                                    } else {
                                        updatedOrderIndex = call.getOrder().intValue();
                                    }
                                }
                            }

                            final EstimatedVehicleJourney.EstimatedCalls estimatedCallsObj = estimatedVehicleJourney.getEstimatedCalls();
                            if (estimatedCallsObj != null) {
                                final List<EstimatedCall> estimatedCalls = estimatedCallsObj.getEstimatedCalls();
                                for (EstimatedCall call : estimatedCalls) {
                                    if (call.getOrder() == null) {
                                        updatedOrderIndex++;
                                        if (call.getVisitNumber() != null) {
                                            call.setOrder(call.getVisitNumber());
                                            updatedOrderIndex = call.getVisitNumber().intValue();
                                            ruleAppliedCounter++;
                                        } else {
                                            call.setOrder(BigInteger.valueOf(updatedOrderIndex));
                                            ruleAppliedCounter++;
                                        }
                                    } else {
                                        updatedOrderIndex = call.getOrder().intValue();
                                    }
                                }
                            }
                            if (ruleAppliedCounter > 0) {
                                getMetricsService().registerDataMapping(
                                    SiriDataType.ESTIMATED_TIMETABLE,
                                    datasetId,
                                    ADD_ORDER_TO_CALLS,
                                    ruleAppliedCounter
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}
