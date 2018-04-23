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
import org.w3c.dom.Node;
import uk.org.ifopt.siri20.AccessibilityFeatureEnumeration;
import uk.org.ifopt.siri20.StopPlaceComponentTypeEnumeration;

import javax.xml.bind.ValidationEvent;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.AFFECTED_COMPONENTS;

public class AffectedComponentValidator extends CustomValidator {


    private static final String FIELDNAME = "AffectedComponent";
    private static final String path = AFFECTED_COMPONENTS + "/" + FIELDNAME;


    @Override
    public String getXpath() {
        return path;
    }

    /*

        <AffectedComponent>
            <ComponentRef>...</ComponentRef>
            <ComponentType>...</ComponentType>
            <AccessFeatureType>...</AccessFeatureType>
        </AffectedComponent>
     */

    private Set<String> expectedComponentTypes = Sets.newHashSet(
            StopPlaceComponentTypeEnumeration.ACCESS_SPACE.value(),
            StopPlaceComponentTypeEnumeration.BOARDING_POSITION.value(),
            StopPlaceComponentTypeEnumeration.ENTRANCE.value(),
            StopPlaceComponentTypeEnumeration.QUAY.value()
    );

    private Set<String> expectedAccessFeatureType = Sets.newHashSet(
            AccessibilityFeatureEnumeration.ESCALATOR.value(),
            AccessibilityFeatureEnumeration.LIFT.value(),
            AccessibilityFeatureEnumeration.NARROW_ENTRANCE.value(),
            AccessibilityFeatureEnumeration.RAMP.value(),
            AccessibilityFeatureEnumeration.STAIRS.value()
    );

    @Override
    public ValidationEvent isValid(Node node) {

        String componentType = getChildNodeValue(node, "ComponentType");
        if (componentType == null | !expectedComponentTypes.contains(componentType)) {
            return  createEvent(node, "ComponentType", expectedComponentTypes, componentType, ValidationEvent.ERROR);
        }

        if (StopPlaceComponentTypeEnumeration.QUAY.value().equals(componentType)) {
            String componentRef = getChildNodeValue(node, "ComponentRef");
            if (componentRef == null | !isValidNsrId("NSR:Quay:", componentRef)) {
                return  createEvent(node, "ComponentRef", "NSR:Quay:ID when ComponentType is 'quay'", componentRef, ValidationEvent.FATAL_ERROR);
            }
        }

        String accessFeatureType = getChildNodeValue(node, "AccessFeatureType");
        if (accessFeatureType != null && !expectedAccessFeatureType.contains(accessFeatureType)) {
            return  createEvent(node, "AccessFeatureType", expectedAccessFeatureType, accessFeatureType, ValidationEvent.ERROR);
        }

        return null;
    }
}
