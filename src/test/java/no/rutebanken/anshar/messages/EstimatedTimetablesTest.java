package no.rutebanken.anshar.messages;

import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.VehicleRef;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EstimatedTimetablesTest {


    @Before
    public void setup() {
        EstimatedTimetables.timetableDeliveries.clear();
    }

    @Test
    public void testAddNull() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(null, "test");

        assertTrue(EstimatedTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddJourney() {
        int previousSize = EstimatedTimetables.getAll().size();
        EstimatedVehicleJourney element = createEstimatedVehicleJourney("1234", "4321", 30, ZonedDateTime.now());

        EstimatedTimetables.add(element, "test");

        assertTrue(EstimatedTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(
                createEstimatedVehicleJourney("1111", "4321", 30, ZonedDateTime.now().minusDays(2))
                , "test"
        );

        EstimatedTimetables.timetableDeliveries.removeExpiredElements();

        assertEquals(previousSize, EstimatedTimetables.getAll().size());

        EstimatedTimetables.add(
                createEstimatedVehicleJourney("2222", "4321", 30, ZonedDateTime.now().plusDays(2))
                , "test"
        );

        EstimatedTimetables.timetableDeliveries.removeExpiredElements();

        assertEquals(previousSize + 1, EstimatedTimetables.getAll().size());
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now();
        EstimatedTimetables.add(createEstimatedVehicleJourney("12345", "4321", 30, departure), "test");
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedVehicleJourney("12345", "4321", 30, departure), "test");
        assertTrue("Updating Journey added element.", EstimatedTimetables.getAll().size() == expectedSize);

        ZonedDateTime departure_2 = ZonedDateTime.now();
        EstimatedTimetables.add(createEstimatedVehicleJourney("54321", "4321", 30, departure_2), "test");
        expectedSize++;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedVehicleJourney("12345", "4321", 30, departure_2), "test2");
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", EstimatedTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test").size() == expectedSize-1);

    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int callCount, ZonedDateTime time) {
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
            call.setAimedArrivalTime(time);
            call.setExpectedArrivalTime(time);
            call.setAimedDepartureTime(time);
            call.setExpectedDepartureTime(time);
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);

        return element;
    }
}
