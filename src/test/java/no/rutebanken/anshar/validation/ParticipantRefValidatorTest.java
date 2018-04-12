package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.ParticipantRefValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ParticipantRefValidatorTest extends CustomValidatorTest {

    static ParticipantRefValidator validator;

    @BeforeClass
    public static void init() {
        validator = new ParticipantRefValidator();
    }

    @Test
    public void testValidParticipant() throws Exception{
        String xml = createXml("ParticipantRef", "ENT");

        assertNull("Valid ParticipantRef flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testInvalidParticipant() throws Exception{
        String xml = createXml("ParticipantRef", "N");

        assertNotNull("Invalid ParticipantRef flagged as valid", validator.isValid(createXmlNode(xml)));
    }
}
