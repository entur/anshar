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

import no.rutebanken.anshar.routes.validation.validators.sx.ProgressValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProgressValidatorTest extends CustomValidatorTest {

    private static ProgressValidator validator;
    private static final String fieldName = "Progress";

    @BeforeAll
    public static void init() {
        validator = new ProgressValidator();
    }

    @Test
    public void testClosedProgress() throws Exception{
        String xml = createXml(fieldName, "closed");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testOpenProgress() throws Exception{
        String xml = createXml(fieldName, "open");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testInvalidProgress() throws Exception{
        String xml = createXml(fieldName, "published");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Invalid "+fieldName+" flagged as valid");
    }
}
