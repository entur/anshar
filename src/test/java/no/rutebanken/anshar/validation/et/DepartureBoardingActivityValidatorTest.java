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

import no.rutebanken.anshar.routes.validation.validators.et.DepartureBoardingActivityValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class DepartureBoardingActivityValidatorTest extends CustomValidatorTest {

    private static DepartureBoardingActivityValidator validator;
    private final String fieldName = "DepartureBoardingActivity";

    @BeforeClass
    public static void init() {
        validator = new DepartureBoardingActivityValidator();
    }

    @Test
    public void testValid() throws Exception{

        assertNull("Valid " + fieldName + " flagged as invalid", validator.isValid(createXmlNode(fieldName, "boarding")));
        assertNull("Valid " + fieldName + " flagged as invalid", validator.isValid(createXmlNode(fieldName, "noBoarding")));
        assertNull("Valid " + fieldName + " flagged as invalid", validator.isValid(createXmlNode(fieldName, "passThru")));
    }
    @Test
    public void testInvalid() throws Exception{

        assertNotNull("Invalid " + fieldName + " flagged as valid", validator.isValid(createXmlNode(fieldName, "alighting")));
    }


}
