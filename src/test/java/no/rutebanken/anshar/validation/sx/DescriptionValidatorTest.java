package no.rutebanken.anshar.validation.sx;

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.sx.DescriptionValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DescriptionValidatorTest extends CustomValidatorTest {


    private static DescriptionValidator validator;

    @BeforeAll
    public static void init() {
        validator = new DescriptionValidator();
    }

    @Test
    public void testHtmlCodeInDescription() throws Exception{
        String xml = "<PLACEHOLDER><Description>&lt;b&gt;lorem ipsum&lt;/b&gt;</Description></PLACEHOLDER>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(
            valid,
            "Description with HTML-code flagged as valid"
        );
    }
}
