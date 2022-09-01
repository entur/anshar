package no.rutebanken.anshar.util;

import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;

import java.io.Serializable;

public class SiriUtils implements Serializable {
    public static String resolveServiceJourneyId(EstimatedVehicleJourney estimatedVehicleJourney) {
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = estimatedVehicleJourney.getFramedVehicleJourneyRef();
        if (framedVehicleJourneyRef != null) {
            return framedVehicleJourneyRef.getDatedVehicleJourneyRef();
        } else if (estimatedVehicleJourney.getDatedVehicleJourneyRef() != null) {
            return estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue();
        } else if (estimatedVehicleJourney.getEstimatedVehicleJourneyCode() != null) {
            return estimatedVehicleJourney.getEstimatedVehicleJourneyCode();
        }
        return null;
    }
}