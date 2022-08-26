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
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.createCombinedId;

public class CodespaceProcessor extends ValueAdapter implements PostProcessor {

    private final String codespace;

    public CodespaceProcessor(String codespace) {
        this.codespace = codespace;
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
                            String original = estimatedVehicleJourney.getDataSource();
                            estimatedVehicleJourney.setDataSource(getMappedCodespace(original));
                        }
                    }
                }
            }

            List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
            if (situationExchangeDeliveries != null) {
                for (SituationExchangeDeliveryStructure situationExchangeDelivery : situationExchangeDeliveries) {
                    SituationExchangeDeliveryStructure.Situations situations = situationExchangeDelivery.getSituations();
                    if (situations != null && situations.getPtSituationElements() != null) {
                        for (PtSituationElement ptSituationElement : situations.getPtSituationElements()) {

                            String original = null;
                            if (ptSituationElement.getParticipantRef() != null) {
                                original = ptSituationElement.getParticipantRef().getValue();
                            }

                            RequestorRef participantRef = new RequestorRef();
                            participantRef.setValue(getMappedCodespace(original));
                            ptSituationElement.setParticipantRef(participantRef);
                        }
                    }
                }
            }

            List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
            if (vehicleMonitoringDeliveries != null) {
                for (VehicleMonitoringDeliveryStructure vehicleMonitoringDeliveryStructure : vehicleMonitoringDeliveries) {
                    List<VehicleActivityStructure> vehicleActivities = vehicleMonitoringDeliveryStructure.getVehicleActivities();
                    if (vehicleActivities != null) {
                        for (VehicleActivityStructure vehicleActivity : vehicleActivities) {

                            if (vehicleActivity.getMonitoredVehicleJourney() != null) {
                                String original = vehicleActivity.getMonitoredVehicleJourney().getDataSource();
                                vehicleActivity.getMonitoredVehicleJourney().setDataSource(getMappedCodespace(original));
                            }
                        }
                    }
                }
            }
        }
    }

    private String getMappedCodespace(String original) {
        if (original == null || original.equals(codespace)) {
            return codespace;
        }
        return createCombinedId(original, codespace);
    }
}
