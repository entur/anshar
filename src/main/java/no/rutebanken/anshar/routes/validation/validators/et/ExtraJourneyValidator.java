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

package no.rutebanken.anshar.routes.validation.validators.et;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.org.siri.siri20.VehicleModesEnumeration;

import javax.xml.bind.ValidationEvent;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class ExtraJourneyValidator extends CustomValidator {

    private static final String FIELDNAME = "ExtraJourney";
    private static final String path = ESTIMATED_VEHICLE_JOURNEY + "/" + FIELDNAME;


    private static final Set<String> validVehicleModes = Sets.newHashSet(
            VehicleModesEnumeration.AIR.value(),
            VehicleModesEnumeration.BUS.value(),
            VehicleModesEnumeration.COACH.value(),
            VehicleModesEnumeration.FERRY.value(),
            VehicleModesEnumeration.METRO.value(),
            VehicleModesEnumeration.RAIL.value(),
            VehicleModesEnumeration.TRAM.value());

    private static final String vehicleModeNodeName = "VehicleMode";
    private static final String routeRefNodeName = "RouteRef";
    private static final String groupOfLinesRef = "GroupOfLinesRef";
    private static final String estimatedVehicleJourneyCodeRef = "EstimatedVehicleJourneyCode";

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String isExtraJourney = getNodeValue(node);

        if (isExtraJourney == null) {
            return  createEvent(node, vehicleModeNodeName, "not null when present", FIELDNAME, ValidationEvent.ERROR);
        }

        if (Boolean.valueOf(isExtraJourney)) {
            // ExtraJourney == true

            // VehicleMode - required
            final String vehicleMode = getSiblingNodeValue(node, vehicleModeNodeName);
            if (vehicleMode == null) {
                return  createEvent(node, vehicleModeNodeName, "not null when ExtraJourney=true", vehicleMode, ValidationEvent.ERROR);
            }
            if (!validVehicleModes.contains(vehicleMode)) {
                return  createEvent(node, vehicleModeNodeName, validVehicleModes, vehicleMode, ValidationEvent.ERROR);
            }


            // RouteRef - required
            final String routeRef = getSiblingNodeValue(node, routeRefNodeName);
            if (routeRef == null) {
                return  createEvent(node, routeRefNodeName, "not null when ExtraJourney=true", routeRef, ValidationEvent.ERROR);
            }
            if (!routeRef.contains(":Route:")) {
                return  createEvent(node, routeRefNodeName, "valid RouteRef - CODESPACE:Route:ID", routeRef, ValidationEvent.ERROR);
            }

            // GroupOfLinesRef - required
            final String groupOfLines = getSiblingNodeValue(node, groupOfLinesRef);
            if (groupOfLines == null) {
                return  createEvent(node, groupOfLinesRef, "not null when ExtraJourney=true", groupOfLines, ValidationEvent.ERROR);
            }
            if (!groupOfLines.contains(":Network:")) {
                return  createEvent(node, groupOfLinesRef, "valid GroupOfLinesRef - CODESPACE:Network:ID", groupOfLines, ValidationEvent.ERROR);
            }

            // EstimatedVehicleJourneyCode - required
            final String estimatedVehicleJourneyCode = getSiblingNodeValue(node, estimatedVehicleJourneyCodeRef);
            if (estimatedVehicleJourneyCode == null) {
                return  createEvent(node, estimatedVehicleJourneyCodeRef, "not null when ExtraJourney=true", groupOfLines, ValidationEvent.ERROR);
            }
        }


        return null;
    }
}
