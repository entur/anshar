package no.rutebanken.anshar.messages;

import org.junit.Test;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.VehicleRef;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertTrue;

public class EstimatedTimetablesTest {


    @Test
    public void testAddNull() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(null, "test");

        assertTrue(EstimatedTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddJourney() {
        int previousSize = EstimatedTimetables.getAll().size();
        EstimatedVehicleJourney element = createEstimatedVehicleJourney("1234", "4321", 30);

        EstimatedTimetables.add(element, "test");

        assertTrue(EstimatedTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(
                createEstimatedVehicleJourney("1234", "4321", 30)
                , "test"
        );

        assertTrue(EstimatedTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(createEstimatedVehicleJourney("12345", "4321", 30), "test");
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedVehicleJourney("12345", "4321", 30), "test");
        assertTrue("Updating Journey added element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedVehicleJourney("54321", "4321", 30), "test");
        expectedSize++;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedVehicleJourney("12345", "4321", 30), "test2");
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", EstimatedTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test").size() == expectedSize-1);

    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int callCount) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < callCount; i++) {

            EstimatedCall call = new EstimatedCall();
            call.setAimedArrivalTime(ZonedDateTime.now());
            call.setExpectedArrivalTime(ZonedDateTime.now());
            call.setAimedDepartureTime(ZonedDateTime.now());
            call.setExpectedDepartureTime(ZonedDateTime.now());
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);

        return element;
    }
}
