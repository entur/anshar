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

package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.ReportTypeValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ReportTypeValidatorTest extends CustomValidatorTest {

    static ReportTypeValidator validator;

    @BeforeClass
    public static void init() {
        validator = new ReportTypeValidator();
    }

    @Test
    public void testGeneralReportType() throws Exception{
        String xml = createXml("ReportType", "general");

        assertNull("Valid Progress flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testIncidentReportTypes() throws Exception{
        String xml = createXml("ReportType", "incident");

        assertNull("Valid Progress flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testInvalidProgress() throws Exception{
        String xml = createXml("ReportType", "published");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull("Invalid ParticipantRef flagged as valid", valid);
    }
}
