package no.rutebanken.anshar;

import no.rutebanken.anshar.messages.Vehicles;
import org.junit.Test;
import uk.org.siri.siri20.CourseOfJourneyRefStructure;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleRef;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class VehiclesTest {

    @Test
    public void testAddVehicle() {
        int previousSize = Vehicles.getAll().size();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        Vehicles.add(element);
        assertTrue(Vehicles.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredVehicle() {
        int previousSize = Vehicles.getAll().size();

        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().minusMinutes(1), UUID.randomUUID().toString());

        Vehicles.add(element);
        assertTrue(Vehicles.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedVehicle() {
        int previousSize = Vehicles.getAll().size();

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        Vehicles.add(element);
        //Verify that element is added
        assertTrue(Vehicles.getAll().size() == previousSize+1);

        //Update element
        VehicleActivityStructure element2 = createVehicleActivityStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        Vehicles.add(element2);
        //Verify that existing element is updated
        assertTrue(Vehicles.getAll().size() == previousSize+1);

        //Add brand new element
        VehicleActivityStructure element3 = createVehicleActivityStructure(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        Vehicles.add(element3);
        //Verify that new element is added
        assertTrue(Vehicles.getAll().size() == previousSize+2);
    }

    private VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime validUntilTime, String vehicleReference) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setValidUntilTime(validUntilTime);

        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef vRef = new VehicleRef();
        vRef.setValue(vehicleReference);
        vehicleJourney.setVehicleRef(vRef);

        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
        journeyRefStructure.setValue("yadayada");
        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);

        element.setMonitoredVehicleJourney(vehicleJourney);
        return element;
    }
}
