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
import uk.org.siri.siri21.DatedVehicleJourneyRef;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.Siri;

import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.REMOVE_INVALID_CODESPACE;

public class CodespaceWhiteListProcessor extends ValueAdapter implements PostProcessor {

    private final String subscriptionIdentifier;
    private final List<String> whitelist;

    private PrometheusMetricsService metrics;

    public CodespaceWhiteListProcessor(String subscriptionIdentifier, List<String> codespaceWhiteList) {
        this.subscriptionIdentifier = subscriptionIdentifier;
        this.whitelist = codespaceWhiteList;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (this.whitelist == null || this.whitelist.isEmpty()) {
            //Nothing to do - return immediately
            return;
        }
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                        int size = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();

                        estimatedJourneyVersionFrame
                                .getEstimatedVehicleJourneies()
                                .removeIf(et -> isInvalidCodespace(et.getFramedVehicleJourneyRef()));

                        estimatedJourneyVersionFrame
                                .getEstimatedVehicleJourneies()
                                .removeIf(et -> isInvalidCodespace(et.getDatedVehicleJourneyRef()));

                        estimatedJourneyVersionFrame
                                .getEstimatedVehicleJourneies()
                                .removeIf(et -> isInvalidCodespace(et.getEstimatedVehicleJourneyCode()));

                        estimatedJourneyVersionFrame
                                .getEstimatedVehicleJourneies()
                                .removeIf(et -> isInvalidCodespace(et.getLineRef()));

                        if (estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size() != size) {
                            final int removedDataCount = size - estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();
                            getMetricsService()
                                    .registerDataMapping(
                                            SiriDataType.ESTIMATED_TIMETABLE,
                                            subscriptionIdentifier,
                                            REMOVE_INVALID_CODESPACE,
                                            removedDataCount
                                    );
                        }
                    }
                }
            }
        }

    }

    private boolean isInvalidCodespace(LineRef lineRef) {
        if (lineRef != null) {
            return toBeRemoved(lineRef.getValue());
        }
        return false;
    }
    private boolean isInvalidCodespace(String s) {
        if (s != null) {
            return toBeRemoved(s);
        }
        return false;
    }
    private boolean isInvalidCodespace(DatedVehicleJourneyRef datedVehicleJourneyRef) {
        if (datedVehicleJourneyRef != null) {
            return toBeRemoved(datedVehicleJourneyRef.getValue());
        }
        return false;
    }

    private boolean isInvalidCodespace(FramedVehicleJourneyRefStructure framedVehicleJourneyRef) {
        if (framedVehicleJourneyRef != null) {
            String datedVehicleJourneyRef = framedVehicleJourneyRef.getDatedVehicleJourneyRef();
            return toBeRemoved(datedVehicleJourneyRef);
        }
        return false;
    }

    private boolean toBeRemoved(String datedVehicleJourneyRef) {
        for (String codespace : whitelist) {
            if (!datedVehicleJourneyRef.startsWith(codespace))  {
                return true;
            }
        }
        return false;
    }

    void prepareMetrics() {
        if (metrics == null) {
            metrics = ApplicationContextHolder.getContext().getBean(PrometheusMetricsService.class);
        }
    }
}
