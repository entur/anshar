package no.rutebanken.anshar.data;

import no.rutebanken.anshar.App;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class VehicleActivitiesTest {


    @Autowired
    private VehicleActivities vehicleActivities;

    @Before
    public void setup() {

    }

    @Test
    public void testAddVehicle() {
        int previousSize = vehicleActivities.getAll().size();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        vehicleActivities.add("test", element);
        assertEquals("Vehicle not added", previousSize + 1, vehicleActivities.getAll().size());
    }

    @Test
    public void testEmptyLocation() {
        int previousSize = vehicleActivities.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        element.getMonitoredVehicleJourney().setVehicleLocation(new LocationStructure());

        vehicleActivities.add("test", element);
        assertEquals("Activity without location was added", previousSize, vehicleActivities.getAll().size());
    }

    @Test
    public void testOrigoLocation() {
        int previousSize = vehicleActivities.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.ZERO);
        location.setLongitude(BigDecimal.ZERO);
        element.getMonitoredVehicleJourney().setVehicleLocation(location);

        vehicleActivities.add("test", element);
        assertEquals("Activity without location set to (0, 0) was added", previousSize, vehicleActivities.getAll().size());
    }

    @Test
    public void testNullVehicle() {
        int previousSize = vehicleActivities.getAll().size();

        vehicleActivities.add("test", null);
        assertEquals("Null-element added", previousSize, vehicleActivities.getAll().size());
    }

    @Test
    public void testUpdatedVehicle() {
        int previousSize = vehicleActivities.getAll().size();

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        vehicleActivities.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, vehicleActivities.getAll().size());

        //Update element
        VehicleActivityStructure element2 = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        VehicleActivityStructure updatedVehicle = vehicleActivities.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedVehicle);
        //Verify that existing element is updated
        assertTrue(vehicleActivities.getAll().size() == previousSize + 1);

        //Add brand new element
        VehicleActivityStructure element3 = createVehicleActivityStructure(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        updatedVehicle = vehicleActivities.add("test", element3);

        //Verify that activity is found as new
        assertNotNull(updatedVehicle);
        //Verify that new element is added
        assertEquals(previousSize + 2, vehicleActivities.getAll().size());

        vehicleActivities.add("test2", element3);
        //Verify that new element is added
        assertEquals(previousSize + 3, vehicleActivities.getAll().size());

        //Verify that element added is vendor-specific
        assertEquals(previousSize + 2, vehicleActivities.getAll("test").size());
    }

    @Test
    public void testUpdatedVehicleWrongOrder() {

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);
        ProgressBetweenStopsStructure progress = new ProgressBetweenStopsStructure();
        progress.setPercentage(BigDecimal.ONE);
        element.setProgressBetweenStops(progress);
        element.setRecordedAtTime(ZonedDateTime.now().plusMinutes(1));

        vehicleActivities.add("test", element);

        VehicleActivityStructure testOriginal = vehicleActivities.add("test", element);

        assertEquals("VM has not been added.", BigDecimal.ONE, testOriginal.getProgressBetweenStops().getPercentage());

        //Update element
        VehicleActivityStructure element2 = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        ProgressBetweenStopsStructure progress2 = new ProgressBetweenStopsStructure();
        progress2.setPercentage(BigDecimal.TEN);
        element2.setProgressBetweenStops(progress2);

        //Update is recorder BEFORE current - should be ignored
        element2.setRecordedAtTime(ZonedDateTime.now());

        VehicleActivityStructure test = vehicleActivities.add("test", element2);

        assertEquals("VM has been wrongfully updated", BigDecimal.ONE, test.getProgressBetweenStops().getPercentage());
    }

    @Test
    public void testUpdatedVehicleNoRecordedAtTime() {

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);
        ProgressBetweenStopsStructure progress = new ProgressBetweenStopsStructure();
        progress.setPercentage(BigDecimal.ONE);
        element.setProgressBetweenStops(progress);
        element.setRecordedAtTime(null);

        vehicleActivities.add("test", element);

        VehicleActivityStructure testOriginal = vehicleActivities.add("test", element);

        assertEquals("VM has not been added.", BigDecimal.ONE, testOriginal.getProgressBetweenStops().getPercentage());

        //Update element
        VehicleActivityStructure element2 = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        ProgressBetweenStopsStructure progress2 = new ProgressBetweenStopsStructure();
        progress2.setPercentage(BigDecimal.TEN);
        element2.setProgressBetweenStops(progress2);

        element2.setRecordedAtTime(null);

        VehicleActivityStructure test = vehicleActivities.add("test", element2);

        assertEquals("VM has been wrongfully updated", BigDecimal.ONE, test.getProgressBetweenStops().getPercentage());
    }

    @Test
    public void testGetUpdatesOnly() {
        int previousSize = vehicleActivities.getAll().size();

        String prefix = "updateOnly-";
        vehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), prefix+"1234"));
        vehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), prefix+"2345"));
        vehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), prefix+"3456"));
        // Added 3
        assertEquals(previousSize+3, vehicleActivities.getAllUpdates("1234-1234", null).size());

        vehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), prefix+"4567"));

        //Added one
        assertEquals(1, vehicleActivities.getAllUpdates("1234-1234", null).size());


        //None added
        assertEquals(0, vehicleActivities.getAllUpdates("1234-1234", null).size());

        //Verify that all elements still exist
        assertEquals(previousSize+4, vehicleActivities.getAll().size());
    }

    private VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime recordedAtTime, String vehicleReference) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(recordedAtTime);
        element.setValidUntilTime(recordedAtTime.plusMinutes(10));

        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef vRef = new VehicleRef();
        vRef.setValue(vehicleReference);
        vehicleJourney.setVehicleRef(vRef);
        /*
        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(10.63));
        location.setLongitude(BigDecimal.valueOf(63.10));
        vehicleJourney.setVehicleLocation(location);
*/
        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
        journeyRefStructure.setValue("yadayada");
        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);

        element.setMonitoredVehicleJourney(vehicleJourney);
        return element;
    }
}
