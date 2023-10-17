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

package no.rutebanken.anshar.validation.sx;

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.sx.AffectedComponentValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import uk.org.ifopt.siri21.AccessibilityFeatureEnumeration;
import uk.org.ifopt.siri21.StopPlaceComponentTypeEnumeration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AffectedComponentValidatorTest extends CustomValidatorTest {

    @Autowired
    private AffectedComponentValidator validator;
    
    @BeforeEach
    public void init() {
        validator.prepareTestData("NSR:Quay:1234");
    }

    @Test
    public void testValidComponents() throws Exception {
        Node node = createAffectedComponentNode(null,
                StopPlaceComponentTypeEnumeration.ACCESS_SPACE.value(),
                AccessibilityFeatureEnumeration.ESCALATOR.value());

        assertNull(validator.isValid(node));

        node = createAffectedComponentNode("NSR:Quay:1234",
                StopPlaceComponentTypeEnumeration.QUAY.value(),
                AccessibilityFeatureEnumeration.ESCALATOR.value());

        assertNull(validator.isValid(node));
    }

    @Test
    public void testInvalidComponentRef() throws Exception {

        Node node = createAffectedComponentNode("1234",
                StopPlaceComponentTypeEnumeration.QUAY.value(),
                AccessibilityFeatureEnumeration.ESCALATOR.value());

        ValidationEvent valid = validator.isValid(node);
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains("ComponentRef"));
    }

    @Test
    public void testInvalidComponentRefForNonQuay() throws Exception {

        Node node = createAffectedComponentNode("1234",
                StopPlaceComponentTypeEnumeration.ENTRANCE.value(),
                AccessibilityFeatureEnumeration.ESCALATOR.value());

        ValidationEvent valid = validator.isValid(node);
        assertNull(valid);
    }

    @Test
    public void testInvalidComponentType() throws Exception {

        Node node = createAffectedComponentNode("1234",
                StopPlaceComponentTypeEnumeration.STOPPING_PLACE.value(),
                AccessibilityFeatureEnumeration.ESCALATOR.value());

        ValidationEvent valid = validator.isValid(node);
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains("ComponentType"));
    }

    @Test
    public void testInvalidAccessFeatureType() throws Exception {

        Node node = createAffectedComponentNode("1234",
                StopPlaceComponentTypeEnumeration.ENTRANCE.value(),
                AccessibilityFeatureEnumeration.QUEUE_MANAGEMENT.value());

        ValidationEvent valid = validator.isValid(node);
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains("AccessFeatureType"));
    }


    /**
     * Creates example - all fields are required for valid XML
     * <AffectedComponent>
     *   <ComponentRef>...</ComponentRef>
     *   <ComponentType>...</ComponentType>
     *   <AccessFeatureType>...</AccessFeatureType>
     * </AffectedComponent>
     *
     */
    private Node createAffectedComponentNode(String componentRef, String componentType, String accessFeatureType) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<AffectedComponent>\n");

        if (componentRef != null) {
            xml.append("  <ComponentRef>").append(componentRef).append("</ComponentRef>\n");
        }

        if (componentType != null) {
            xml.append("  <ComponentType>").append(componentType).append("</ComponentType>\n");
        }
        if (accessFeatureType != null) {
            xml.append("  <AccessFeatureType>").append(accessFeatureType).append("</AccessFeatureType>\n");
        }

        xml.append("</AffectedComponent>");

        Node node = createXmlNode(xml.toString());
        return node;
    }

}
