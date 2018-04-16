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
import uk.org.siri.siri20.*;

import java.util.List;

public class BaneNorArrivalDepartureCancellationProcessor extends ValueAdapter implements PostProcessor {

    public BaneNorArrivalDepartureCancellationProcessor() {
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {
            processEtDeliveries(siri.getServiceDelivery().getEstimatedTimetableDeliveries());
        }
    }

    private void processEtDeliveries(List<EstimatedTimetableDeliveryStructure> etDeliveries) {
        for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
            List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
            for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {

                    EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney.getRecordedCalls();

                    boolean wasPreviousCallCancelled = (estimatedVehicleJourney.isIsCompleteStopSequence() != null && estimatedVehicleJourney.isIsCompleteStopSequence());

                    boolean hasRecordedCalls = false;

                    if (recordedCalls != null && recordedCalls.getRecordedCalls() != null) {
                        RecordedCall recordedCall = recordedCalls.getRecordedCalls().get(recordedCalls.getRecordedCalls().size()-1);
                        //Only need to check last RecordedCall - if it exists
                        wasPreviousCallCancelled = recordedCall.isCancellation() != null && recordedCall.isCancellation();
                        hasRecordedCalls = true;
                    }

                    EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();

                    if (estimatedCalls != null && estimatedCalls.getEstimatedCalls() != null) {
                        List<EstimatedCall> calls = estimatedCalls.getEstimatedCalls();
                        for (int i = 0; i < calls.size()-1; i++) {
                            EstimatedCall call = calls.get(i);
                            EstimatedCall nextCall = calls.get(i+1);

                            boolean isCallCancelled = call.isCancellation() != null && call.isCancellation();
                            boolean isNextCallCancelled = nextCall.isCancellation() != null && nextCall.isCancellation();

                            if (isCallCancelled) {
                                if (!isNextCallCancelled && !hasRecordedCalls  && i == 0) {
                                    // Only first stop is cancelled
                                    // Cancellation is true, departure from current is cancelled, arrival on next is cancelled

                                    call.setDepartureStatus(CallStatusEnumeration.CANCELLED);
                                    call.setCancellation(true);
                                    nextCall.setArrivalStatus(CallStatusEnumeration.CANCELLED);

                                } else if (!wasPreviousCallCancelled && !isNextCallCancelled) {
                                    // Single stop cancellation - arrival AND departure is cancelled
                                    call.setArrivalStatus(CallStatusEnumeration.CANCELLED);
                                    call.setDepartureStatus(CallStatusEnumeration.CANCELLED);
                                    call.setCancellation(true);
                                } else {
                                    // Multiple stops are cancelled - arrival/departure should be set accordingly
                                    if (wasPreviousCallCancelled) {
                                        call.setArrivalStatus(CallStatusEnumeration.CANCELLED);
                                    } else {
                                        call.setCancellation(false);
                                        // Setting ArrivalStatus to onTime - may be wrong, but currently, value is not used
                                        call.setArrivalStatus(CallStatusEnumeration.ON_TIME);
                                    }

                                    if (isNextCallCancelled) {
                                        call.setDepartureStatus(CallStatusEnumeration.CANCELLED);
                                    } else {
                                        call.setCancellation(false);
                                        // Setting DepartureStatus to onTime - may be wrong, but currently, value is not used
                                        call.setDepartureStatus(CallStatusEnumeration.ON_TIME);
                                    }
                                }
                            } else if (isNextCallCancelled && i == calls.size()-2) {
                                //Only last (next) stop is cancelled. Cancel departure from current
                                call.setDepartureStatus(CallStatusEnumeration.CANCELLED);
                            }

                            wasPreviousCallCancelled = isCallCancelled;
                        }
                    }

                }
            }
        }

    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
