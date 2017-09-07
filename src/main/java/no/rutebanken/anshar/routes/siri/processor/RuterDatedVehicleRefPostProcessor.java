package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.Siri;

import java.util.List;
import java.util.StringTokenizer;

public class RuterDatedVehicleRefPostProcessor extends ValueAdapter implements PostProcessor {

    private static final String DELIMITER = ":";
    static final String SERVICE_JOURNEY_PREFIX = "RUT:ServiceJourney:";

    @Override
    public void process(Siri siri) {
        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (estimatedTimetableDeliveries != null) {
            for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                if (estimatedJourneyVersionFrames != null) {
                    for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : estimatedJourneyVersionFrames) {
                        if (estimatedVersionFrameStructure != null) {
                            for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVersionFrameStructure.getEstimatedVehicleJourneies()) {
                                if (estimatedVehicleJourney.getFramedVehicleJourneyRef() != null) {
                                    String datedVehicleJourneyRef = estimatedVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
                                    if (datedVehicleJourneyRef != null && !datedVehicleJourneyRef.startsWith(SERVICE_JOURNEY_PREFIX)) {
                                        StringTokenizer tokenizer = new StringTokenizer(datedVehicleJourneyRef, DELIMITER);
                                        String newValue = SERVICE_JOURNEY_PREFIX + tokenizer.nextToken() + "-" + tokenizer.nextToken();
                                        estimatedVehicleJourney.getFramedVehicleJourneyRef()
                                                .setDatedVehicleJourneyRef(datedVehicleJourneyRef + SiriValueTransformer.SEPARATOR + newValue);
                                    }
                                }
                            }
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
