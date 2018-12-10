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

import no.rutebanken.anshar.routes.validation.validators.et.EstimatedVehicleJourneyValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.*;

public class EstimatedVehicleJourneyValidatorTest extends CustomValidatorTest {

    private static EstimatedVehicleJourneyValidator validator;
    private final String fieldName = "EstimatedVehicleJourney";
    private String lineRef;
    private String directionRef;
    private String framedVehicleJourneyRef;
    private String dataSource;
    private String isCompleteStopSequence;

    private String extraJourney;
    private String groupOfLinesRef;
    private String externalLineRef;
    private String estimatedVehicleJourneyCode;
    private String routeRef;

    @BeforeClass
    public static void init() {
        validator = new EstimatedVehicleJourneyValidator();
    }

    @Before
    public void initTestValues() {
        lineRef =                   createXml("LineRef", "1234");
        directionRef =              createXml("DirectionRef", "1234");
        framedVehicleJourneyRef =   createXml("FramedVehicleJourneyRef", "1234");
        dataSource =                createXml("DataSource", "1234");
        isCompleteStopSequence =    createXml("IsCompleteStopSequence", "1234");

        extraJourney =              createXml("ExtraJourney", "true");
        groupOfLinesRef =           createXml("GroupOfLinesRef", "1234");
        externalLineRef =           createXml("ExternalLineRef", "1234");
        estimatedVehicleJourneyCode=createXml("EstimatedVehicleJourneyCode", "1234");
        routeRef =                  createXml("RouteRef", "1234");
    }

    @Test
    public void testCompleteEstimatedVehicleJourney() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, framedVehicleJourneyRef, dataSource, isCompleteStopSequence);

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(node));
    }

    @Test
    public void testMissingLineRef() throws Exception{
        Node node = createEstimatedVehicleJourney(null, directionRef, framedVehicleJourneyRef, dataSource, isCompleteStopSequence);

        assertNotNull("Missing LineRef flagged as valid", validator.isValid(node));
    }

    @Test
    public void testMissingDirectionRef() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, null, framedVehicleJourneyRef, dataSource, isCompleteStopSequence);

        assertNotNull("Missing DirectionRef flagged as valid", validator.isValid(node));
    }

    @Test
    public void testMissingFramedVehicleJourneyRef() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, null, dataSource, isCompleteStopSequence);

        assertNotNull("Missing FramedVehicleJourneyRef flagged as valid", validator.isValid(node));
    }

    @Test
    public void testMissingDataSource() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, framedVehicleJourneyRef, null, isCompleteStopSequence);

        assertNotNull("Missing DataSource flagged as valid", validator.isValid(node));
    }

    @Test
    public void testMissingIsCompleteStopSequence() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, framedVehicleJourneyRef, dataSource, null);

        assertNotNull("Missing IsCompleteStopSequence flagged as valid", validator.isValid(node));
    }

    @Test
    public void testMissingLineRefAndDataSource() throws Exception{
        Node node = createEstimatedVehicleJourney(null, directionRef, framedVehicleJourneyRef, null, isCompleteStopSequence);

        ValidationEvent validation = validator.isValid(node);
        assertNotNull("Missing IsCompleteStopSequence flagged as valid", validation);

        assertTrue(validation.getMessage().contains("LineRef"));
        assertTrue(validation.getMessage().contains("DataSource"));
    }

    @Test
    public void testCompleteExtraJourney() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, extraJourney, groupOfLinesRef, externalLineRef, estimatedVehicleJourneyCode, routeRef, dataSource, isCompleteStopSequence);

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(node));
    }

    @Test
    public void testCompleteEstimatedVehicleJourneyWithFalseExtraJourney() throws Exception{
        extraJourney =              createXml("ExtraJourney", "false");

        Node node = createEstimatedVehicleJourney(extraJourney, lineRef, directionRef, framedVehicleJourneyRef, dataSource, isCompleteStopSequence);

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(node));
    }

    @Test
    public void testExtraJourneyMissingGroupOfLinesRef() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, extraJourney , externalLineRef, estimatedVehicleJourneyCode, routeRef, dataSource, isCompleteStopSequence);

        ValidationEvent validation = validator.isValid(node);
        assertNotNull("Missing GroupOfLinesRef flagged as valid", validation);

        assertTrue(validation.getMessage().contains("GroupOfLinesRef"));
    }

    @Test
    public void testExtraJourneyMissingExternalLineRef() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, extraJourney , groupOfLinesRef, estimatedVehicleJourneyCode, routeRef, dataSource, isCompleteStopSequence);

        ValidationEvent validation = validator.isValid(node);
        assertNotNull("Missing ExternalLineRef flagged as valid", validation);

        assertTrue(validation.getMessage().contains("ExternalLineRef"));
    }

    @Test
    public void testExtraJourneyMissingEstimatedVehicleJourneyCode() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, extraJourney , groupOfLinesRef, externalLineRef, routeRef, dataSource, isCompleteStopSequence);

        ValidationEvent validation = validator.isValid(node);
        assertNotNull("Missing EstimatedVehicleJourneyCode flagged as valid", validation);

        assertTrue(validation.getMessage().contains("EstimatedVehicleJourneyCode"));
    }

    @Test
    public void testExtraJourneyMissingRouteRef() throws Exception{
        Node node = createEstimatedVehicleJourney(lineRef, directionRef, extraJourney, groupOfLinesRef, externalLineRef, estimatedVehicleJourneyCode, dataSource, isCompleteStopSequence);

        ValidationEvent validation = validator.isValid(node);
        assertNotNull("Missing RouteRef flagged as valid", validation);

        assertTrue(validation.getMessage().contains("RouteRef"));
    }

    private Node createEstimatedVehicleJourney(String... xmlData) {

        return createXmlNode(mergeXml(xmlData));
    }
}
