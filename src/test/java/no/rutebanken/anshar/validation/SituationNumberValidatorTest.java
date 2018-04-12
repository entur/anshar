package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.SituationNumberValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class SituationNumberValidatorTest extends CustomValidatorTest {

    static SituationNumberValidator validator;

    @BeforeClass
    public static void init() {
        validator = new SituationNumberValidator();
    }

    @Test
    public void testNumberedSituationNumber() throws Exception{
        String xml = createXml("SituationNumber", "1234");

        assertNotNull("Valid SituationNumber flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testCombinedSituationNumber() throws Exception{
        String xml = createXml("SituationNumber", "status-12344321");

        assertNotNull("Valid SituationNumber flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testValieSituationNumber() throws Exception{
        String xml = createXml("SituationNumber", "ENT:SituationNumber:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Invalid SituationNumber flagged as valid", valid);
    }
}
