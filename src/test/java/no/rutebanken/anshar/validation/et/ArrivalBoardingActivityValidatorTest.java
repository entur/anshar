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

package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.ArrivalBoardingActivityValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ArrivalBoardingActivityValidatorTest extends CustomValidatorTest {

    private static ArrivalBoardingActivityValidator validator;
    private final String fieldName = "ArrivalBoardingActivity";

    @BeforeAll
    public static void init() {
        validator = new ArrivalBoardingActivityValidator();
    }

    @Test
    public void testValid() throws Exception{

        assertNull("Valid " + fieldName + " flagged as invalid", validator.isValid(createXmlNode(fieldName, "alighting")));
        assertNull("Valid " + fieldName + " flagged as invalid", validator.isValid(createXmlNode(fieldName, "noAlighting")));
        assertNull("Valid " + fieldName + " flagged as invalid", validator.isValid(createXmlNode(fieldName, "passThru")));
    }
    @Test
    public void testInvalid() throws Exception{

        assertNotNull("Invalid " + fieldName + " flagged as valid", validator.isValid(createXmlNode(fieldName, "boarding")));
    }


}
