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

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
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

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.UPDATED_CODESPACE;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.createCombinedId;

public class CodespaceProcessor extends ValueAdapter implements PostProcessor {

    private final String codespace;

    private PrometheusMetricsService metrics;

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
                            String mappedCodespace = getMappedCodespace(original);

                            if (original != null && !original.equals(mappedCodespace)) {
                                estimatedVehicleJourney.setDataSource(mappedCodespace);

                                getMetricsService()
                                        .registerDataMapping(
                                                SiriDataType.ESTIMATED_TIMETABLE,
                                                codespace,
                                                UPDATED_CODESPACE,
                                                1
                                        );
                            }
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

                            String mappedCodespace = getMappedCodespace(original);

                            if (original != null && !original.equals(mappedCodespace)) {
                                RequestorRef participantRef = new RequestorRef();
                                participantRef.setValue(mappedCodespace);
                                ptSituationElement.setParticipantRef(participantRef);

                                getMetricsService()
                                        .registerDataMapping(
                                                SiriDataType.SITUATION_EXCHANGE,
                                                codespace,
                                                UPDATED_CODESPACE,
                                                1
                                        );
                            }
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
                                String mappedCodespace = getMappedCodespace(original);

                                if (original != null && !original.equals(mappedCodespace)) {
                                    vehicleActivity.getMonitoredVehicleJourney().setDataSource(mappedCodespace);

                                    getMetricsService()
                                            .registerDataMapping(
                                                    SiriDataType.VEHICLE_MONITORING,
                                                    codespace,
                                                    UPDATED_CODESPACE,
                                                    1
                                            );
                                }
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

    void prepareMetrics() {
        if (metrics == null) {
            metrics = ApplicationContextHolder.getContext().getBean(PrometheusMetricsService.class);
        }
    }
}
