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

import no.rutebanken.anshar.routes.validation.validators.SummaryValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class SummaryValidatorTest extends CustomValidatorTest {

    static SummaryValidator validator;

    @BeforeClass
    public static void init() {
        validator = new SummaryValidator();
    }

    @Test
    public void testEmptySummary() throws Exception{
        String xml = "<PLACEHOLDER><Summary></Summary></PLACEHOLDER>";

        assertNotNull("Empty Summary flagged as valid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testValidSummary() throws Exception{
        String xml = "<PLACEHOLDER><Summary>lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Valid Summary flagged as invalid", valid);
    }

    @Test
    public void testMultipleSummariesWithLanguage() throws Exception{
        String xml = "<PLACEHOLDER><Summary lang=\"NO\">lorem ipsum</Summary><Summary lang=\"EN\">lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Multiple summaries with language flagged as invalid", valid);
    }

    @Test
    public void testMultipleSummariesWithSameLanguage() throws Exception{
        String xml = "<PLACEHOLDER><Summary lang=\"NO\">lorem ipsum</Summary><Summary lang=\"NO\">lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull("Multiple summaries with same language flagged as valid", valid);
    }

    @Test
    public void testMultipleSummariesWithoutLanguage() throws Exception{
        String xml = "<PLACEHOLDER><Summary >lorem ipsum</Summary><Summary >lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull("Multiple summaries without language flagged as valid", valid);
    }
}
