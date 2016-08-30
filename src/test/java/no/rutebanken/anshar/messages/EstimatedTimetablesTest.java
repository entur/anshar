package no.rutebanken.anshar.messages;

import org.junit.Test;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

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
        EstimatedTimetableDeliveryStructure element = createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1));

        EstimatedTimetables.add(element, "test");

        assertTrue(EstimatedTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = EstimatedTimetables.getAll().size();

        EstimatedTimetables.add(
                createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().minusMinutes(1))
                , "test"
        );

        assertTrue(EstimatedTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = EstimatedTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        EstimatedTimetables.add(createEstimatedTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)), "test");
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)), "test");
        assertTrue("Updating Journey added element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)), "test");
        expectedSize++;
        assertTrue("Adding Journey did not add element.", EstimatedTimetables.getAll().size() == expectedSize);

        EstimatedTimetables.add(createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)), "test2");
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", EstimatedTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", EstimatedTimetables.getAll("test").size() == expectedSize-1);

    }

    private EstimatedTimetableDeliveryStructure createEstimatedTimetableDeliveryStructure(String version, ZonedDateTime validUntil) {
        EstimatedTimetableDeliveryStructure element = new EstimatedTimetableDeliveryStructure();
        element.setValidUntil(validUntil);
        element.setVersion(version);
        return element;
    }
}
