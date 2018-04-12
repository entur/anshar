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
