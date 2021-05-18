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

package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.LineRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MonitoredLineRefValidatorTest extends CustomValidatorTest {

    private static LineRefValidator validator;
    private final String fieldName = "LineRef";

    @BeforeAll
    public static void init() {
        validator = new LineRefValidator();
    }

    @Test
    public void testValidLine() throws Exception{
        String xml = createXml(fieldName, "ENT:Line:1234");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testInvalidLine() throws Exception{
        String xml = createXml(fieldName, "N11");

        assertNotNull(validator.isValid(createXmlNode(xml)), "Invalid "+fieldName+" flagged as valid");
    }
}
