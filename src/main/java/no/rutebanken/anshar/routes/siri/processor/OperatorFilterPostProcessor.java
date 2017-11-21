package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.Siri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OperatorFilterPostProcessor extends ValueAdapter implements PostProcessor {
    private final List<String> operatorsToIgnore;

    /**
     *
     * @param operatorsToIgnore List of OperatorRef-values to remove
     * @param operatorOverrideMapping Defines Operator-override if original should not be used.
     */
    public OperatorFilterPostProcessor(List<String> operatorsToIgnore, Map<String, String> operatorOverrideMapping) {
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
                                    estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                            .removeIf(et -> et.getOperatorRef() != null && operatorsToIgnore.contains(et.getOperatorRef().getValue()));
                                }

                                estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                        .stream()
                                        .forEach(et -> {
                                            if (et.getLineRef() != null && et.getOperatorRef() != null) {
                                                String lineRef = et.getLineRef().getValue();
                                                if (lineRef != null && !lineRef.contains(":Line:")) {
                                                    String operatorRef = et.getOperatorRef().getValue();
                                                    et.getLineRef().setValue(operatorOverrideMapping.getOrDefault(operatorRef, operatorRef) + ":Line:" + lineRef);
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
