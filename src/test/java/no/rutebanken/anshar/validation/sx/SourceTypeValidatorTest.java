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

import no.rutebanken.anshar.routes.validation.validators.sx.SourceTypeValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SourceTypeValidatorTest extends CustomValidatorTest {

    private static SourceTypeValidator validator;
    private final String fieldName = "SourceType";

    @BeforeAll
    public static void init() {
        validator = new SourceTypeValidator();
    }

    @Test
    public void testDirectReportType() throws Exception{
        String xml = createXml(fieldName, "directReport");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testFaxReportType() throws Exception{
        String xml = createXml(fieldName, "fax");

        assertNotNull(validator.isValid(createXmlNode(xml)), "Invalid "+fieldName+" flagged as valid");
    }

    @Test
    public void testEmptySourceType() throws Exception{
        String xml = createXml(fieldName, "");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Invalid "+fieldName+" flagged as valid");
    }
}
