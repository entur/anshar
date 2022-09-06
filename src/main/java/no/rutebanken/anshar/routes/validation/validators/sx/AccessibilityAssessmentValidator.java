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

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.org.acbs.siri21.AccessibilityEnumeration;

import javax.xml.bind.ValidationEvent;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ACCESSIBILITY_ASSESSMENT;

/**
 * Verifies childnodes for element AccessibilityAssessment. Values must be set valid if they exist
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class AccessibilityAssessmentValidator extends CustomValidator {

    private static final String FIELDNAME = "AccessibilityAssessment";

    private static final String LIMITATIONS = "Limitations";
    private static final String ACCESSIBILITY_LIMITATION = "AccessibilityLimitation";
    private static final String WHEELCHAIR_ACCESS = "WheelchairAccess";
    private static final String STEP_FREE_ACCESS = "StepFreeAccess";
    private static final String ESCALATOR_FREE_ACCESS = "EscalatorFreeAccess";
    private static final String LIFT_FREE_ACCESS = "LiftFreeAccess";

    private String path;

    private static final Set<String> expectedValues = Sets.newHashSet(
            AccessibilityEnumeration.TRUE.value(),
            AccessibilityEnumeration.FALSE.value(),
            AccessibilityEnumeration.UNKNOWN.value()
    );

    public AccessibilityAssessmentValidator() {

        path = ACCESSIBILITY_ASSESSMENT + FIELD_DELIMITER + FIELDNAME;

    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        // MobilityImpairedAccess - required
        final String mobilityImpairedAccess = getChildNodeValue(node, "MobilityImpairedAccess");
        if (mobilityImpairedAccess == null || !isBoolean(mobilityImpairedAccess)) {
            return  createEvent(node, FIELDNAME, "true or false", mobilityImpairedAccess, ValidationEvent.FATAL_ERROR);
        }

        final Node limitationsNode = getChildNodeByName(node, LIMITATIONS);
        if (limitationsNode == null) {
            return  createEvent(node, LIMITATIONS, LIMITATIONS, null, ValidationEvent.FATAL_ERROR);
        }
        final Node accessibilityLimitationNode = getChildNodeByName(limitationsNode, ACCESSIBILITY_LIMITATION);
        if (accessibilityLimitationNode == null) {
            return  createEvent(limitationsNode, ACCESSIBILITY_LIMITATION, ACCESSIBILITY_LIMITATION, null, ValidationEvent.FATAL_ERROR);
        }

        ValidationEvent valid = isValid(accessibilityLimitationNode, WHEELCHAIR_ACCESS);
        if (valid != null) {
            return valid;
        }

        valid = isValid(accessibilityLimitationNode, STEP_FREE_ACCESS);
        if (valid != null) {
            return valid;
        }

        valid = isValid(accessibilityLimitationNode, ESCALATOR_FREE_ACCESS);
        if (valid != null) {
            return valid;
        }

        valid = isValid(accessibilityLimitationNode, LIFT_FREE_ACCESS);
        if (valid != null) {
            return valid;
        }

        return null;
    }

    private ValidationEvent isValid(Node accessibilityLimitationNode, String fieldName) {
        if (!expectedValues.contains(getChildNodeValue(accessibilityLimitationNode, fieldName))) {
            return createEvent(accessibilityLimitationNode, fieldName, "one of " + expectedValues, null, ValidationEvent.FATAL_ERROR);
        }
        return null;
    }

    private boolean isBoolean(String value) {
        return ("true".equals(value) || "false".equals(value));
    }
}
