package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.StopPointRefValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class StopPointRefValidatorTest extends CustomValidatorTest {

    static StopPointRefValidator validator;

    @BeforeClass
    public static void init() {
        validator = new StopPointRefValidator();
    }

    @Test
    public void testEmptyStopPointRef() throws Exception{
        String xml = createXml("StopPointRef", "");

        assertNotNull("Empty StopPointRef flagged as valid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testQuayStopPointRef() throws Exception{
        String xml = createXml("StopPointRef", "NSR:Quay:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Valid StopPointRef flagged as invalid", valid);
    }

    @Test
    public void testStopPlaceStopPointRef() throws Exception{
        String xml = createXml("StopPointRef", "NSR:StopPlace:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull("StopPointRef with StopPlace flagged as valid", valid);
    }
}
