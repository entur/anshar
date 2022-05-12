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

import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

/**
 * Verifies that EstimatedVehicleJourney contains required fields
 *
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class EstimatedVehicleJourneyValidator extends CustomValidator {

    private static final String FIELDNAME = "EstimatedVehicleJourney";
    private String path = ESTIMATED_VEHICLE_JOURNEY;

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String extraJourneyValue = getChildNodeValue(node, "ExtraJourney");
        List<String> fieldNames = new ArrayList<>();

        fieldNames.add("LineRef");
        fieldNames.add("DirectionRef");
        fieldNames.add("DataSource");
        fieldNames.add("IsCompleteStopSequence");

        if (extraJourneyValue != null && Boolean.valueOf(extraJourneyValue)) {
            fieldNames.add("EstimatedVehicleJourneyCode");
            fieldNames.add("RouteRef");
            fieldNames.add("GroupOfLinesRef");
            fieldNames.add("ExternalLineRef");
        } else {

            Node framedVehicleJourneyRefNode = getChildNodeByName(node, "FramedVehicleJourneyRef");

            if (framedVehicleJourneyRefNode == null ||
                    getChildNodeValue(framedVehicleJourneyRefNode, "DatedVehicleJourneyRef") == null) {
                if (getChildNodeValue(node,"DatedVehicleJourneyRef") == null) {
                    fieldNames.add("FramedVehicleJourneyRef or DatedVehicleJourneyRef");
                }
            }

        }
        return verifyRequiredFields(node, FIELDNAME, fieldNames.toArray(new String[fieldNames.size()]));
    }
}
