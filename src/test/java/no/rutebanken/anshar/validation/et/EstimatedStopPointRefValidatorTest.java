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

import no.rutebanken.anshar.routes.validation.validators.et.EstimatedStopPointRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class EstimatedStopPointRefValidatorTest extends CustomValidatorTest {

    static EstimatedStopPointRefValidator validator;
    private String fieldName = "StopPointRef";

    @BeforeClass
    public static void init() {
        validator = new EstimatedStopPointRefValidator();
    }

    @Test
    public void testEmptyStopPointRef() throws Exception{
        String xml = createXml(fieldName, "");

        assertNotNull("Empty "+ fieldName +" flagged as valid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testQuayStopPointRef() throws Exception{
        String xml = createXml(fieldName, "NSR:Quay:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Valid "+ fieldName +" flagged as invalid", valid);
    }

    @Test
    public void testStopPlaceStopPointRef() throws Exception{
        String xml = createXml(fieldName, "NSR:StopPlace:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(fieldName +" with StopPlace flagged as valid", valid);
    }
}
