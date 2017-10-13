package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.Siri;

import java.util.List;

public class OperatorFilterPostProcessor extends ValueAdapter implements PostProcessor {
    private final List<String> operators;

    public OperatorFilterPostProcessor(List<String> operators) {
        this.operators = operators;
    }


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

                                estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                        .removeIf(et -> et.getOperatorRef() != null && operators.contains(et.getOperatorRef().getValue()));

                                estimatedVersionFrameStructure.getEstimatedVehicleJourneies()
                                        .removeIf(et -> et.getOperatorRef() != null && operators.contains(et.getOperatorRef().getValue()));
                            }
                        }
                    }
                }
            }
        }
    }
}
