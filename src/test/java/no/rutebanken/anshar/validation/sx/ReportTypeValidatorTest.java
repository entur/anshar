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
import no.rutebanken.anshar.routes.validation.validators.sx.ReportTypeValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ReportTypeValidatorTest extends CustomValidatorTest {

    private static ReportTypeValidator validator;
    private final String fieldName = "ReportType";

    @BeforeAll
    public static void init() {
        validator = new ReportTypeValidator();
    }

    @Test
    public void testGeneralReportType() throws Exception{
        String xml = createXml(fieldName, "general");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testIncidentReportTypes() throws Exception{
        String xml = createXml(fieldName, "incident");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testInvalidProgress() throws Exception{
        String xml = createXml(fieldName, "published");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Invalid "+fieldName+" flagged as valid");
    }
}
