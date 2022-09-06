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
import uk.org.siri.siri21.Siri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.LINE_MAPPING;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.createCombinedId;

public class OperatorFilterPostProcessor extends ValueAdapter implements PostProcessor {
    private static transient final Logger logger = LoggerFactory.getLogger(OperatorFilterPostProcessor.class);

    private final List<String> operatorsToIgnore;
    private final String datasetId;

    /**
     *
     * @param operatorsToIgnore List of OperatorRef-values to remove
     * @param operatorOverrideMapping Defines Operator-override if original should not be used.
     */
    public OperatorFilterPostProcessor(String datasetId, List<String> operatorsToIgnore, Map<String, String> operatorOverrideMapping) {
        this.datasetId = datasetId;
        this.operatorsToIgnore = operatorsToIgnore;
        this.operatorOverrideMapping = operatorOverrideMapping;
    }

    private Map<String, String> operatorOverrideMapping = new HashMap<>();

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null && siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null) {
            List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                if (estimatedTimetableDeliveries != null) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                    if (estimatedJourneyVersionFrames != null) {
                        for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : estimatedJourneyVersionFrames) {
                            if (estimatedVersionFrameStructure != null) {

                                if (operatorsToIgnore != null && !operatorsToIgnore.isEmpty()) {
                                    final int sizeBefore = estimatedVersionFrameStructure
                                        .getEstimatedVehicleJourneies()
                                        .size();

                                    estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                            .removeIf(et -> et.getOperatorRef() != null && operatorsToIgnore.contains(et.getOperatorRef().getValue()));

                                    int ignoredUpdates = sizeBefore - estimatedVersionFrameStructure
                                                                        .getEstimatedVehicleJourneies()
                                                                        .size();

                                    logger.info("Removed {} updates from ignored operators {}", ignoredUpdates, operatorsToIgnore);
                                }

                                estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                        .forEach(et -> {
                                            if (et.getLineRef() != null && et.getOperatorRef() != null) {
                                                String lineRef = et.getLineRef().getValue();
                                                if (lineRef != null) {
                                                    String operatorRef = et.getOperatorRef().getValue();

                                                    String updatedLineRef;
                                                    if (lineRef.contains(":Line:")) {
                                                        updatedLineRef = lineRef;
                                                    } else {
                                                        updatedLineRef = operatorOverrideMapping.getOrDefault(operatorRef, operatorRef) + ":Line:" + lineRef;
                                                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, LINE_MAPPING, 1);
                                                    }

                                                    et.getLineRef().setValue(createCombinedId(lineRef, updatedLineRef));

                                                    // TODO: Should we also update OperatorRef?
//                                                    et.getOperatorRef().setValue(operatorOverrideMapping.getOrDefault(operatorRef, operatorRef));
                                                }
                                            }
                                        });
                            }
                        }
                    }
                }
            }
        }
    }
}
