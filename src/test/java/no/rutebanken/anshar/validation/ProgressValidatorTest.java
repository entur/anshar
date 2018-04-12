package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.ProgressValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ProgressValidatorTest extends CustomValidatorTest {

    static ProgressValidator validator;

    @BeforeClass
    public static void init() {
        validator = new ProgressValidator();
    }

    @Test
    public void testClosedProgress() throws Exception{
        String xml = createXml("Progress", "closed");

        assertNull("Valid Progress flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testOpenProgress() throws Exception{
        String xml = createXml("Progress", "open");

        assertNull("Valid Progress flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testInvalidProgress() throws Exception{
        String xml = createXml("Progress", "published");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull("Invalid Progress flagged as valid", valid);
    }
}
