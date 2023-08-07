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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_CALL;

/**
 * Verifies that EstimatedCall contains required fields
 *
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class EstimatedCallRequiredFieldsValidator extends CallRequiredFieldsValidator {

    private static final String FIELDNAME = "EstimatedCall";
    private String path = ESTIMATED_CALL;

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

        List <String> missingFields = validateCommonFields(node);

        if (getChildNodeByName(node, "ExpectedArrivalTime") == null &&getChildNodeByName(node, "ExpectedDepartureTime") == null) {
            missingFields.add("ExpectedArrivalTime/ExpectedDepartureTime");
        }
        if (!missingFields.isEmpty()) {
            return createMissingFieldEvent(node, FIELDNAME, missingFields, ValidationEvent.WARNING);
        }
        return null;
    }
}
