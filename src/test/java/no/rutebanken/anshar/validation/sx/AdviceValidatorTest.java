package no.rutebanken.anshar.validation.sx;

import no.rutebanken.anshar.routes.validation.validators.sx.AdviceValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;

public class AdviceValidatorTest extends CustomValidatorTest {


    private static AdviceValidator validator;

    @BeforeClass
    public static void init() {
        validator = new AdviceValidator();
    }

    @Test
    public void testHtmlCodeInAdvice() throws Exception{
        String xml = "<PLACEHOLDER><Advice>&lt;b&gt;lorem ipsum&lt;/b&gt;</Advice></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull("Advice with HTML-code flagged as valid", valid);
    }
}
