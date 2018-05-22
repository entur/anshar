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

package no.rutebanken.anshar.validation.sx;

import no.rutebanken.anshar.routes.validation.validators.sx.AffectedVehicleJourneyRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class AffectedVehicleJourneyRefValidatorTest extends CustomValidatorTest {

    private static AffectedVehicleJourneyRefValidator validator;
    private final String fieldName = "VehicleJourneyRef";

    @BeforeClass
    public static void init() {
        validator = new AffectedVehicleJourneyRefValidator();
    }

    @Test
    public void testNumberedSituationNumber() throws Exception{
        String xml = createXml(fieldName, "1234");

        assertNotNull("Valid "+fieldName+" flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testCombinedSituationNumber() throws Exception{
        String xml = createXml(fieldName, "4443333222");

        assertNotNull("Valid "+fieldName+" flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testValieSituationNumber() throws Exception{
        String xml = createXml(fieldName, "ENT:ServiceJourney:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Invalid "+fieldName+" flagged as valid", valid);
    }
}
