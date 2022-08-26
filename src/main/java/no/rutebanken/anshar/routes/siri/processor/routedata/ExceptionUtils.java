package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;

import java.io.Serializable;

public class ExceptionUtils implements Serializable {
    static String resolveServiceJourneyId(EstimatedVehicleJourney estimatedVehicleJourney) {
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = estimatedVehicleJourney.getFramedVehicleJourneyRef();
        if (framedVehicleJourneyRef != null) {
            return framedVehicleJourneyRef.getDatedVehicleJourneyRef();
        } else if (estimatedVehicleJourney.getDatedVehicleJourneyRef() != null) {
            return estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue();
        }
        return null;
    }
}