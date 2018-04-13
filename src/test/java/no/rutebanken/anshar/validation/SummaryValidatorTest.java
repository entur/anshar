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
