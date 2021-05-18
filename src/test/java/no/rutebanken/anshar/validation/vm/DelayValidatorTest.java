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

package no.rutebanken.anshar.validation.vm;

import no.rutebanken.anshar.routes.validation.validators.vm.DelayValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.*;

public class DelayValidatorTest extends CustomValidatorTest{

    private static DelayValidator validator;
    private static final String fieldName = "Delay";

    @BeforeAll
    public static void init() {
        validator = new DelayValidator();
    }

    @Test
    public void testValidDelay() throws Exception {
        assertNull(validator.isValid(createXmlNode(fieldName, "PT0S")));
        assertNull(validator.isValid(createXmlNode(fieldName, "-PT134S")));
    }

    @Test
    public void testInvalidDelay() throws Exception {
        ValidationEvent valid = validator.isValid(createXmlNode(fieldName, "5 min"));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(fieldName));
    }

}
