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