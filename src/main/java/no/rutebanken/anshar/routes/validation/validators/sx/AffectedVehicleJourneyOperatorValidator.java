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

package no.rutebanken.anshar.routes.validation.validators.sx;

import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;

import static no.rutebanken.anshar.routes.validation.validators.Constants.AFFECTED_VEHICLE_JOURNEY;

/**
 * Verifies that the value for field OperatorRef is present and specifies a Codespace ID
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class AffectedVehicleJourneyOperatorValidator extends CustomValidator {

    private static final String FIELDNAME = "Operator";
    private static final String path = AFFECTED_VEHICLE_JOURNEY + "/" + FIELDNAME;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue == null || nodeValue.length() != 3) {
            //TODO: Check for valid Operator
            return createEvent(node, FIELDNAME, "CODESPACE", nodeValue, ValidationEvent.WARNING);
        }

        return null;
    }
}
