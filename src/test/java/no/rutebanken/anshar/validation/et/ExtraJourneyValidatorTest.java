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

import no.rutebanken.anshar.routes.validation.validators.et.ExtraJourneyValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ExtraJourneyValidatorTest extends CustomValidatorTest{

    private static ExtraJourneyValidator validator;
    private String extraJourneyFieldName = "ExtraJourney";
    private String vehicleModeName = "VehicleMode";
    private String routeRefName = "RouteRef";
    private String groupOfLinesName = "GroupOfLinesRef";
    private String estimatedVehicleJourneyCodeName = "EstimatedVehicleJourneyCode";

    private String validVehicleMode = "air";
    private String invalidVehicleMode = "underground";

    private String validRouteRef = "NSR:Route:1234";
    private String invalidRouteRef = "1234";

    private String validGroupOfLinesRef = "NSR:Network:TEST";
    private String invalidGroupOfLines = "TEST";

    private String estimatedVehicleJourneyCode = "NSR.VehicleJourney:1234-1234-1234-EXTRA";

    @BeforeClass
    public static void init() {
        validator = new ExtraJourneyValidator();
    }

    @Test
    public void testExtraJourneyCorrectValues() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "true");
        String vehicleMode = createXml(vehicleModeName, validVehicleMode);
        String routeRef = createXml(routeRefName, validRouteRef);
        String groupOfLines = createXml(groupOfLinesName, validGroupOfLinesRef);
        String estimatedVehicleJourney = createXml(estimatedVehicleJourneyCodeName, estimatedVehicleJourneyCode);

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines, estimatedVehicleJourney);

        assertNull("Correct " + extraJourneyFieldName + " flagged as invalid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testNoExtraJourneyWrongValues() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "false");
        String vehicleMode = createXml(vehicleModeName, invalidVehicleMode);
        String routeRef = createXml(routeRefName, invalidRouteRef);
        String groupOfLines = createXml(groupOfLinesName, invalidGroupOfLines);

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines);

        assertNull("Correct " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testEmptyExtraJourney() throws Exception{
        String xml = createXml(extraJourneyFieldName, "");

        assertNotNull("Empty " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testExtraJourneyNoVehicleMode() throws Exception{
        String xml = createXml(extraJourneyFieldName, "true");

        assertNotNull("Empty " + vehicleModeName + " flagged as valid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testExtraJourneyWrongVehicleMode() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "true");
        String vehicleMode = createXml(vehicleModeName, invalidVehicleMode);
        String routeRef = createXml(routeRefName, validRouteRef);
        String groupOfLines = createXml(groupOfLinesName, validGroupOfLinesRef);

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines);

        assertNotNull("Empty " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testExtraJourneyMissingRoute() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "true");
        String vehicleMode = createXml(vehicleModeName, invalidVehicleMode);
        String routeRef = createXml(routeRefName, "");
        String groupOfLines = createXml(groupOfLinesName, validGroupOfLinesRef);

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines);

        assertNotNull("Empty " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testExtraJourneyWrongRoute() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "true");
        String vehicleMode = createXml(vehicleModeName, invalidVehicleMode);
        String routeRef = createXml(routeRefName, invalidRouteRef);
        String groupOfLines = createXml(groupOfLinesName, validGroupOfLinesRef);

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines);

        assertNotNull("Empty " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testExtraJourneyMissingGroupOfLines() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "true");
        String vehicleMode = createXml(vehicleModeName, invalidVehicleMode);
        String routeRef = createXml(routeRefName, validRouteRef);
        String groupOfLines = createXml(groupOfLinesName, "");

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines);

        assertNotNull("Empty " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }

    @Test
    public void testExtraJourneyWrongGroupOfLines() throws Exception{
        String extraJourney = createXml(extraJourneyFieldName, "true");
        String vehicleMode = createXml(vehicleModeName, invalidVehicleMode);
        String routeRef = createXml(routeRefName, validRouteRef);
        String groupOfLines = createXml(groupOfLinesName, invalidGroupOfLines);

        String xml = mergeXml(extraJourney, vehicleMode, routeRef, groupOfLines);

        assertNotNull("Empty " + extraJourneyFieldName + " flagged as valid", validator.isValid(createXmlNode(xml).getFirstChild()));
    }
}
