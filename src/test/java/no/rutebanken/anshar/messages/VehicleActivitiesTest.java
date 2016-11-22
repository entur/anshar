package no.rutebanken.anshar.messages;

import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.CourseOfJourneyRefStructure;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleRef;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.*;

public class VehicleActivitiesTest {


    @Before
    public void setup() {
        VehicleActivities.vehicleActivities.clear();
    }

    @Test
    public void testAddVehicle() {
        int previousSize = VehicleActivities.getAll().size();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        VehicleActivities.add("test", element);
        assertTrue(VehicleActivities.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredVehicle() {
        int previousSize = VehicleActivities.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().minusMinutes(11), UUID.randomUUID().toString());

        VehicleActivities.add("test", element);

        VehicleActivities.vehicleActivities.removeExpiredElements();
        assertTrue(VehicleActivities.getAll().size() == previousSize);
    }

    @Test
    public void testEmptyLocation() {
        int previousSize = VehicleActivities.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        element.getMonitoredVehicleJourney().setVehicleLocation(new LocationStructure());

        VehicleActivities.add("test", element);
        assertTrue(VehicleActivities.getAll().size() == previousSize);
    }

    @Test
    public void testNullVehicle() {
        int previousSize = VehicleActivities.getAll().size();

        VehicleActivities.add("test", null);
        assertTrue(VehicleActivities.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedVehicle() {
        int previousSize = VehicleActivities.getAll().size();

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        VehicleActivities.add("test", element);
        //Verify that element is added
        assertTrue(VehicleActivities.getAll().size() == previousSize+1);

        //Update element
        VehicleActivityStructure element2 = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        VehicleActivityStructure updatedVehicle = VehicleActivities.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedVehicle);
        //Verify that existing element is updated
        assertTrue(VehicleActivities.getAll().size() == previousSize + 1);

        //Add brand new element
        VehicleActivityStructure element3 = createVehicleActivityStructure(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        updatedVehicle = VehicleActivities.add("test", element3);

        //Verify that activity is found as new
        assertNotNull(updatedVehicle);
        //Verify that new element is added
        assertTrue(VehicleActivities.getAll().size() == previousSize+2);

        VehicleActivities.add("test2", element3);
        //Verify that new element is added
        assertTrue(VehicleActivities.getAll().size() == previousSize+3);

        //Verify that element added is vendor-specific
        assertTrue(VehicleActivities.getAll("test").size() == previousSize+2);
    }

    @Test
    public void testGetUpdatesOnly() {

        assertEquals(0, VehicleActivities.getAll().size());

        VehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), "1234"));
        VehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), "2345"));
        VehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), "3456"));
        // Added 3
        assertEquals(3, VehicleActivities.getAllUpdates("1234-1234").size());

        VehicleActivities.add("test", createVehicleActivityStructure(ZonedDateTime.now(), "4567"));

        //Added one
        assertEquals(1, VehicleActivities.getAllUpdates("1234-1234").size());


        //None added
        assertEquals(0, VehicleActivities.getAllUpdates("1234-1234").size());

        //Verify that all elements still exist
        assertEquals(4, VehicleActivities.getAll().size());
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
