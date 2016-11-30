package no.rutebanken.anshar.messages;

import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.VehicleRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;

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

        EstimatedTimetables.add("test", null);

        assertTrue(EstimatedTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddJourney() {
        int previousSize = EstimatedTimetables.getAll().size();
        EstimatedVehicleJourney element = createEstimatedVehicleJourney("1234", "4321", 0, 30, ZonedDateTime.now().plusMinutes(1), true);

        EstimatedTimetables.add("test", element);

        assertTrue(EstimatedTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testGetUpdatesOnly() {

        assertEquals(0, EstimatedTimetables.getAll().size());

        EstimatedTimetables.add("test", createEstimatedVehicleJourney("1234", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        EstimatedTimetables.add("test", createEstimatedVehicleJourney("2345", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        EstimatedTimetables.add("test", createEstimatedVehicleJourney("3456", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        // Added 3
        assertEquals(3, EstimatedTimetables.getAllUpdates("1234-1234").size());

        EstimatedTimetables.add("test", createEstimatedVehicleJourney("4567", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));

        //Added one
        assertEquals(1, EstimatedTimetables.getAllUpdates("1234-1234").size());


        //None added
        assertEquals(0, EstimatedTimetables.getAllUpdates("1234-1234").size());

        //Verify that all elements still exist
        assertEquals(4, EstimatedTimetables.getAll().size());
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(
                "test", createEstimatedVehicleJourney("1111", "4321", 0, 30, ZonedDateTime.now().minusDays(2), true)
        );

        EstimatedTimetables.timetableDeliveries.removeExpiredElements();

        assertEquals(previousSize, EstimatedTimetables.getAll().size());

        EstimatedTimetables.add(
                "test", createEstimatedVehicleJourney("2222", "4321", 0, 30, ZonedDateTime.now().plusDays(2), true)
        );

        EstimatedTimetables.timetableDeliveries.removeExpiredElements();

        assertEquals(previousSize + 1, EstimatedTimetables.getAll().size());
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        EstimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure, true));
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure, true));
        assertTrue("Updating Journey added element.", EstimatedTimetables.getAll().size() == expectedSize);

        ZonedDateTime departure_2 = ZonedDateTime.now().plusHours(1);
        EstimatedTimetables.add("test", createEstimatedVehicleJourney("54321", "4321", 0, 30, departure_2, true));
        expectedSize++;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add("test2", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure_2, true));
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", EstimatedTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test").size() == expectedSize-1);

    }

    @Test
    public void testUpdatedJourneyWrongOrder() {
        int previousSize = EstimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney("12345", "4321", 0, 10, departure, true);
        estimatedVehicleJourney.setRecordedAtTime(ZonedDateTime.now().plusMinutes(1));

        EstimatedTimetables.add("test", estimatedVehicleJourney);
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedVehicleJourney estimatedVehicleJourney1 = createEstimatedVehicleJourney("12345", "4321", 1, 20, departure, true);
        estimatedVehicleJourney1.setRecordedAtTime(ZonedDateTime.now());
        EstimatedTimetables.add("test", estimatedVehicleJourney1);

        assertTrue("Updating Journey added element.", EstimatedTimetables.getAll().size() == expectedSize);

        List<EstimatedCall> estimatedCallsList = EstimatedTimetables.getAll().get(0).getEstimatedCalls().getEstimatedCalls();
        int size = estimatedCallsList.size();
        assertEquals("Older request should have been ignored.", 10, size);
    }

    @Test
    public void testUpdatedJourneyNoRecordedAtTime() {
        int previousSize = EstimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney("12345", "4321", 0, 10, departure, true);
        estimatedVehicleJourney.setRecordedAtTime(null);

        EstimatedTimetables.add("test", estimatedVehicleJourney);
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedVehicleJourney estimatedVehicleJourney1 = createEstimatedVehicleJourney("12345", "4321", 1, 20, departure, true);
        estimatedVehicleJourney1.setRecordedAtTime(null);
        EstimatedTimetables.add("test", estimatedVehicleJourney1);

        assertTrue("Updating Journey added element.", EstimatedTimetables.getAll().size() == expectedSize);

        List<EstimatedCall> estimatedCallsList = EstimatedTimetables.getAll().get(0).getEstimatedCalls().getEstimatedCalls();
        int size = estimatedCallsList.size();
        assertEquals("Older request should have been ignored.", 10, size);
    }

    @Test
    public void testPartiallyUpdatedJourney() {

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        //Adding ET-data with stops 0-20
        EstimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 20, departure, false));
        assertEquals("Adding Journey did not add element.", 1, EstimatedTimetables.getAll().size());

        //Adding ET-data with stops 10-30
        ZonedDateTime updatedDeparture = ZonedDateTime.now().plusHours(2);
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney("12345", "4321", 10, 30, updatedDeparture, false);

        List<EstimatedCall> callList = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
        EstimatedCall lastCall = callList.get(callList.size() - 1);
        assertEquals(updatedDeparture, lastCall.getExpectedArrivalTime());

        EstimatedTimetables.add("test", estimatedVehicleJourney);
        assertEquals("Updating Journey should not add element.", 1, EstimatedTimetables.getAll().size());

        List<EstimatedCall> estimatedCallsList = EstimatedTimetables.getAll().get(0).getEstimatedCalls().getEstimatedCalls();
        int size = estimatedCallsList.size();
        assertEquals("List of EstimatedCalls have not been joined as expected.", 30, size);

        assertEquals("Original call has wrong timestamp", departure, estimatedCallsList.get(0).getExpectedArrivalTime());
        assertEquals("Updated call has wrong timestamp", updatedDeparture, estimatedCallsList.get(estimatedCallsList.size()-1).getExpectedArrivalTime());
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime time, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(isComplete);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = startOrder; i < callCount; i++) {

            EstimatedCall call = new EstimatedCall();
                call.setAimedArrivalTime(time);
                call.setExpectedArrivalTime(time);
                call.setAimedDepartureTime(time);
                call.setExpectedDepartureTime(time);
                call.setOrder(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
