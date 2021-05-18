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

import no.rutebanken.anshar.routes.validation.validators.sx.StopConditionValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StopConditionValidatorTest extends CustomValidatorTest {

    private static StopConditionValidator validator;
    private final String fieldName = "StopCondition";

    @BeforeAll
    public static void init() {
        validator = new StopConditionValidator();
    }

    @Test
    public void testEmptyStopCondition() throws Exception{
        String xml = createXml(fieldName, "");

        assertNotNull(validator.isValid(createXmlNode(xml)),"Empty "+fieldName+ " flagged as valid");
    }

    @Test
    public void testValidStopCondition() throws Exception{

        assertNull(validator.isValid(createXmlNode(fieldName, "exceptionalStop")), "Valid "+fieldName+" flagged as invalid");
        assertNull(validator.isValid(createXmlNode(fieldName, "destination")), "Valid "+fieldName+" flagged as invalid");
        assertNull(validator.isValid(createXmlNode(fieldName, "notStopping")), "Valid "+fieldName+" flagged as invalid");
        assertNull(validator.isValid(createXmlNode(fieldName, "requestStop")), "Valid "+fieldName+" flagged as invalid");
        assertNull(validator.isValid(createXmlNode(fieldName, "startPoint")), "Valid "+fieldName+" flagged as invalid");
        assertNull(validator.isValid(createXmlNode(fieldName, "stop")), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testInvalidStopCondition() throws Exception{

        assertNotNull(validator.isValid(createXmlNode(fieldName, "additionalStop")), "Valid "+fieldName+" flagged as invalid");

    }
}
