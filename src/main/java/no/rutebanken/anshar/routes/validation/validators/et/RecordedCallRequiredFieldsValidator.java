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

import static no.rutebanken.anshar.routes.validation.validators.Constants.RECORDED_CALL;

/**
 * Verifies that RecordedCall contains required fields
 *
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class RecordedCallRequiredFieldsValidator extends CallRequiredFieldsValidator {

    private static final String FIELDNAME = "RecordedCall";
    private String path = RECORDED_CALL;

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

        if (getChildNodeByName(node, "AimedArrivalTime") != null && getChildNodeByName(node, "ActualArrivalTime") == null) {
            // AimedArrival is set, but neither Actual- nor ExpectedArrivalTime
            if (getChildNodeByName(node, "ExpectedArrivalTime") == null) {
                missingFields.add("ExpectedArrivalTime or ActualArrivalTime");
            }
        }

        if (getChildNodeByName(node, "AimedDepartureTime") != null && getChildNodeByName(node, "ActualDepartureTime") == null) {
            // AimedDeparture is set, but neither Actual- nor ExpectedDeparture
            if (getChildNodeByName(node, "ExpectedDepartureTime") == null) {
                missingFields.add("ExpectedDepartureTime or ActualDepartureTime");
            }
        }

        if (!missingFields.isEmpty()) {
            return createMissingFieldEvent(node, FIELDNAME, missingFields, ValidationEvent.WARNING);
        }
        return null;
    }
}
