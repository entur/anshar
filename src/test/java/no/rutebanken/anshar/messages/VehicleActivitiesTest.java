package no.rutebanken.anshar.messages;

import org.junit.Test;
import uk.org.siri.siri20.CourseOfJourneyRefStructure;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleRef;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class VehicleActivitiesTest {

    @Test
    public void testAddVehicle() {
        int previousSize = VehicleActivities.getAll().size();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        VehicleActivities.add(element, "test");
        assertTrue(VehicleActivities.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredVehicle() {
        int previousSize = VehicleActivities.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().minusMinutes(1), UUID.randomUUID().toString());

        VehicleActivities.add(element, "test");
        assertTrue(VehicleActivities.getAll().size() == previousSize);
    }

    @Test
    public void testEmptyLocation() {
        int previousSize = VehicleActivities.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        element.getMonitoredVehicleJourney().setVehicleLocation(new LocationStructure());

        VehicleActivities.add(element, "test");
        assertTrue(VehicleActivities.getAll().size() == previousSize);
    }

    @Test
    public void testNullVehicle() {
        int previousSize = VehicleActivities.getAll().size();

        VehicleActivities.add(null, "test");
        assertTrue(VehicleActivities.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedVehicle() {
        int previousSize = VehicleActivities.getAll().size();

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        VehicleActivities.add(element, "test");
        //Verify that element is added
        assertTrue(VehicleActivities.getAll().size() == previousSize+1);

        //Update element
        VehicleActivityStructure element2 = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        VehicleActivities.add(element2, "test");
        //Verify that existing element is updated
        assertTrue(VehicleActivities.getAll().size() == previousSize+1);

        //Add brand new element
        VehicleActivityStructure element3 = createVehicleActivityStructure(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        VehicleActivities.add(element3, "test");
        //Verify that new element is added
        assertTrue(VehicleActivities.getAll().size() == previousSize+2);

        VehicleActivities.add(element3, "test2");
        //Verify that new element is added
        assertTrue(VehicleActivities.getAll().size() == previousSize+3);

        //Verify that element added is vendor-specific
        assertTrue(VehicleActivities.getAll("test").size() == previousSize+2);
    }

    private VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime validUntilTime, String vehicleReference) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setValidUntilTime(validUntilTime);

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
