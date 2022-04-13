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

import no.rutebanken.anshar.routes.validation.validators.et.EtDatedVehicleJourneyRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EtDatedVehicleJourneyRefValidatorTest extends CustomValidatorTest {

    private static EtDatedVehicleJourneyRefValidator validator;
    private final String fieldName = "DatedVehicleJourneyRef";

    @BeforeAll
    public static void init() {
        validator = new EtDatedVehicleJourneyRefValidator();
    }

    @Test
    public void testServiceJourneyInDatedVehicleJourney() throws Exception{
        Node node = createFramedVehicleJourneyRef("TTT:ServiceJourney:1234");

        assertNotNull(validator.isValid(node), "Invalid "+fieldName+" flagged as valid");
    }

    @Test
    public void testValidDatedVehicleJourneyRef() throws Exception{
        Node node = createFramedVehicleJourneyRef("TTT:DatedServiceJourney:1234");

        assertNull(validator.isValid(node), "Valid DatedVehicleJourneyRef flagged as invalid");
    }

    private Node createFramedVehicleJourneyRef(String framedVehicleJourney) {
        StringBuilder xml = new StringBuilder();

        xml.append("    <DatedVehicleJourneyRef>").append(framedVehicleJourney).append("</DatedVehicleJourneyRef>\n");

        Node node = createXmlNode(xml.toString());
        return node;
    }
}
