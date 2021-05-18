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

import no.rutebanken.anshar.routes.validation.validators.sx.SummaryValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SummaryValidatorTest extends CustomValidatorTest {

    private static SummaryValidator validator;

    @BeforeAll
    public static void init() {
        validator = new SummaryValidator();
    }

    @Test
    public void testEmptySummary() throws Exception{
        String xml = "<PLACEHOLDER><Summary></Summary></PLACEHOLDER>";

        assertNotNull(validator.isValid(createXmlNode(xml)), "Empty Summary flagged as valid");
    }

    @Test
    public void testValidSummary() throws Exception{
        String xml = "<PLACEHOLDER><Summary>lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull(valid, "Valid Summary flagged as invalid");
    }

    @Test
    public void testHtmlCodeInSummary() throws Exception{
        String xml = "<PLACEHOLDER><Summary>&lt;b&gt;lorem ipsum&lt;/b&gt;</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Summary with HTML-code flagged as valid");
    }

    @Test
    public void testTooLongSummary() throws Exception{
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < 161; i++) {
            msg.append("a");
        }
        String xml = "<PLACEHOLDER><Summary>" + msg + "</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Valid Summary flagged as invalid");
    }

    @Test
    public void testMultipleSummariesWithLanguage() throws Exception{
        String xml = "<PLACEHOLDER><Summary lang=\"NO\">lorem ipsum</Summary><Summary lang=\"EN\">lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull(valid, "Multiple summaries with language flagged as invalid");
    }

    @Test
    public void testMultipleSummariesWithSameLanguage() throws Exception{
        String xml = "<PLACEHOLDER><Summary lang=\"NO\">lorem ipsum</Summary><Summary lang=\"NO\">lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Multiple summaries with same language flagged as valid");
    }

    @Test
    public void testMultipleSummariesWithoutLanguage() throws Exception{
        String xml = "<PLACEHOLDER><Summary >lorem ipsum</Summary><Summary >lorem ipsum</Summary></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, "Multiple summaries without language flagged as valid");
    }
}
