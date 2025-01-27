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
import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.org.ifopt.siri21.AccessibilityFeatureEnumeration;
import uk.org.ifopt.siri21.StopPlaceComponentTypeEnumeration;

import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.AFFECTED_COMPONENTS;

/**
 * Verifies the field AffectedComponent and childnodes
 *  - ComponentType is present and is one of the allowed types
 *  - ComponentRef is present and built up correctly
 *  - AccessFeatureType is present and is one of the allowed types
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class AffectedComponentValidator extends CustomValidator {


    private static final String FIELDNAME = "AffectedComponent";
    private String path = AFFECTED_COMPONENTS + FIELD_DELIMITER + FIELDNAME;


    @Override
    public String getXpath() {
        return path;
    }

    private final Set<String> expectedComponentTypes = Sets.newHashSet(
            StopPlaceComponentTypeEnumeration.ACCESS_SPACE.value(),
            StopPlaceComponentTypeEnumeration.BOARDING_POSITION.value(),
            StopPlaceComponentTypeEnumeration.ENTRANCE.value(),
            StopPlaceComponentTypeEnumeration.QUAY.value()
    );

    private final Set<String> expectedAccessFeatureType = Sets.newHashSet(
            AccessibilityFeatureEnumeration.ESCALATOR.value(),
            AccessibilityFeatureEnumeration.LIFT.value(),
            AccessibilityFeatureEnumeration.NARROW_ENTRANCE.value(),
            AccessibilityFeatureEnumeration.RAMP.value(),
            AccessibilityFeatureEnumeration.STAIRS.value()
    );

    @Override
    public ValidationEvent isValid(Node node) {

        String componentType = getChildNodeValue(node, "ComponentType");
        if (componentType == null || !expectedComponentTypes.contains(componentType)) {
            return  createEvent(node, "ComponentType", expectedComponentTypes, componentType, ValidationEvent.ERROR);
        }

        if (StopPlaceComponentTypeEnumeration.QUAY.value().equals(componentType)) {
            String componentRef = getChildNodeValue(node, "ComponentRef");
            if (componentRef == null || !isValidNsrId("NSR:Quay:", componentRef)) {
                return  createEvent(node, "ComponentRef", "NSR:Quay:ID when ComponentType is 'quay'", componentRef, ValidationEvent.FATAL_ERROR);
            }
            if (componentRef == null || !idExists(componentRef)) {
                return  createEvent(node, "ComponentRef", "Valid quay-ID from NSR when ComponentType is 'quay'", componentRef, ValidationEvent.FATAL_ERROR);
            }
        }

        String accessFeatureType = getChildNodeValue(node, "AccessFeatureType");
        if (accessFeatureType != null && !expectedAccessFeatureType.contains(accessFeatureType)) {
            return  createEvent(node, "AccessFeatureType", expectedAccessFeatureType, accessFeatureType, ValidationEvent.ERROR);
        }

        return null;
    }
}
