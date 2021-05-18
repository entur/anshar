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

import no.rutebanken.anshar.routes.validation.validators.et.EtFramedVehicleJourneyRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EtFramedVehicleJourneyRefValidatorTest extends CustomValidatorTest {

    private static EtFramedVehicleJourneyRefValidator validator;
    private final String fieldName = "FramedVehicleJourneyRef";

    @BeforeAll
    public static void init() {
        validator = new EtFramedVehicleJourneyRefValidator();
    }

    @Test
    public void testCompleteFramedVehicleJourney() throws Exception{
        Node node = createFramedVehicleJourneyRef("2018-12-31", "TTT:ServiceJourney:1234");

        assertNull(validator.isValid(node), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testEmptyDataFrameRef() throws Exception{
        Node node = createFramedVehicleJourneyRef("", "TTT:ServiceJourney:1234");

        assertNotNull(validator.isValid(node), "Empty DataFrameRef flagged as valid");
    }

    @Test
    public void testInvalidFramedVehicleJourneyRef() throws Exception{
        Node node = createFramedVehicleJourneyRef("2018-12-31", "1234");

        assertNotNull(validator.isValid(node), "Invalid FramedVehicleJourneyRef flagged as valid");
    }

    @Test
    public void testInvalidDateFrameRef() throws Exception{
        Node node = createFramedVehicleJourneyRef("1122334455", "TTT:ServiceJourney:123");

        assertNotNull(validator.isValid(node), "Invalid FramedVehicleJourneyRef flagged as valid");
    }

    private Node createFramedVehicleJourneyRef(String dataFrameRef, String framedVehicleJourney) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<FramedVehicleJourneyRef>\n");

        if (dataFrameRef != null) {
            xml.append("    <DataFrameRef>").append(dataFrameRef).append("</DataFrameRef>\n");
        }
        if (framedVehicleJourney != null) {
            xml.append("    <DatedVehicleJourneyRef>").append(framedVehicleJourney).append("</DatedVehicleJourneyRef>\n");
        }

        xml.append("    </FramedVehicleJourneyRef>");

        Node node = createXmlNode(xml.toString());
        return node;
    }
}
