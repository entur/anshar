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

import no.rutebanken.anshar.routes.validation.validators.sx.AccessibilityAssessmentValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import uk.org.acbs.siri21.AccessibilityEnumeration;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.acbs.siri21.AccessibilityEnumeration.FALSE;
import static uk.org.acbs.siri21.AccessibilityEnumeration.TRUE;
import static uk.org.acbs.siri21.AccessibilityEnumeration.UNKNOWN;

public class AccessibilityAssessmentValidatorTest extends CustomValidatorTest {

    private static AccessibilityAssessmentValidator validator;

    @BeforeAll
    public static void init() {
        validator = new AccessibilityAssessmentValidator();
    }

    @Test
    public void testNoMobilityImpairedAccess() throws Exception{
        ValidationEvent valid = validator.isValid(createAccessibilityNode(null, null, null, null, null));
        assertNotNull(valid);
    }

    @Test
    public void testOnlyMobilityImpairedAccess() throws Exception{
        ValidationEvent valid = validator.isValid(createAccessibilityNode(false, null, null, null, null));
        assertNotNull(valid);

        valid = validator.isValid(createAccessibilityNode(true, null, null, null, null));
        assertNotNull(valid);
    }

    @Test
    public void testWithNullLimitationsValues() throws Exception{
        ValidationEvent valid = validator.isValid(createAccessibilityNode(false, null, TRUE, TRUE, TRUE));
        assertNotNull(valid);

        assertTrue(valid.getMessage().contains("WheelchairAccess"));

        valid = validator.isValid(createAccessibilityNode(false, TRUE, null, TRUE, TRUE));
        assertNotNull(valid);

        assertTrue(valid.getMessage().contains("StepFreeAccess"));

        valid = validator.isValid(createAccessibilityNode(false, TRUE, TRUE, null, TRUE));
        assertNotNull(valid);

        assertTrue(valid.getMessage().contains("EscalatorFreeAccess"));

        valid = validator.isValid(createAccessibilityNode(false, TRUE, TRUE, TRUE, null));
        assertNotNull(valid);

        assertTrue(valid.getMessage().contains("LiftFreeAccess"));
    }

    @Test
    public void testWithUnknownLimitationsValues() throws Exception{
        ValidationEvent valid = validator.isValid(createAccessibilityNode(false, FALSE, UNKNOWN, UNKNOWN, UNKNOWN));
        assertNull(valid);

        valid = validator.isValid(createAccessibilityNode(false, UNKNOWN, FALSE, UNKNOWN, UNKNOWN));
        assertNull(valid);

        valid = validator.isValid(createAccessibilityNode(false, UNKNOWN, FALSE, UNKNOWN, UNKNOWN));
        assertNull(valid);

        valid = validator.isValid(createAccessibilityNode(false, UNKNOWN, TRUE, FALSE, UNKNOWN));
        assertNull(valid);

    }


    /**
     * Creates example - all fields are required for valid XML
     * <AccessibilityAssessment>
     *   <MobilityImpairedAccess>true</MobilityImpairedAccess>
     *   <Limitations>
     *     <AccessibilityLimitation>
     *         <WheelchairAccess>true</WheelchairAccess>
     *         <StepFreeAccess>true</StepFreeAccess>
     *          <EscalatorFreeAccess>true</EscalatorFreeAccess>
     *          <LiftFreeAccess>true</LiftFreeAccess>
     *     </AccessibilityLimitation>
     *   </Limitations>
     * </AccessibilityAssessment>
     *
     * @param mobilityImpairedAccess
     */
    private Node createAccessibilityNode(Boolean mobilityImpairedAccess, AccessibilityEnumeration wheelchairAccess, AccessibilityEnumeration stepFreeAccess, AccessibilityEnumeration escalatorFreeAccess, AccessibilityEnumeration liftFreeAccess) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<AccessibilityAssessment>\n");

        if (mobilityImpairedAccess != null) {
            xml.append("        <MobilityImpairedAccess>").append(mobilityImpairedAccess).append("</MobilityImpairedAccess>\n");
        }

        if (wheelchairAccess != null || stepFreeAccess != null || escalatorFreeAccess != null || liftFreeAccess != null) {
            xml.append("        <Limitations>\n");
            xml.append("            <AccessibilityLimitation>\n");
            if (wheelchairAccess != null) {
                xml.append("                <WheelchairAccess>").append(wheelchairAccess.value()).append("</WheelchairAccess>\n");
            }
            if (stepFreeAccess != null) {
                xml.append("                <StepFreeAccess>").append(stepFreeAccess.value()).append("</StepFreeAccess>\n");
            }
            if (escalatorFreeAccess != null) {
                xml.append("                <EscalatorFreeAccess>").append(escalatorFreeAccess.value()).append("</EscalatorFreeAccess>\n");
            }
            if (liftFreeAccess != null) {
                xml.append("                <LiftFreeAccess>").append(liftFreeAccess.value()).append("</LiftFreeAccess>\n");
            }
            xml.append("            </AccessibilityLimitation>\n");
            xml.append("        </Limitations>\n");
        }

        xml.append("    </AccessibilityAssessment>");

        Node node = createXmlNode(xml.toString());
        return node;
    }

}
