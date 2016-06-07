package no.rutebanken.anshar;

import no.rutebanken.anshar.messages.Journeys;
import org.junit.Test;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class JourneysTest {

    @Test
    public void testAddJourney() {
        int previousSize = Journeys.getAll().size();
        EstimatedTimetableDeliveryStructure element = createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1));

        Journeys.add(element);

        assertTrue(Journeys.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = Journeys.getAll().size();

        Journeys.add(
                createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().minusMinutes(1))
        );

        assertTrue(Journeys.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = Journeys.getAll().size();
        String version = UUID.randomUUID().toString();

        Journeys.add(createEstimatedTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)));
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", Journeys.getAll().size() == expectedSize);

        Journeys.add(createEstimatedTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)));
        assertTrue("Updating Journey added element.", Journeys.getAll().size() == expectedSize);

        Journeys.add(createEstimatedTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)));
        expectedSize++;
        assertTrue("Adding Journey did not add element.", Journeys.getAll().size() == expectedSize);

    }

    private EstimatedTimetableDeliveryStructure createEstimatedTimetableDeliveryStructure(String version, ZonedDateTime validUntil) {
        EstimatedTimetableDeliveryStructure element = new EstimatedTimetableDeliveryStructure();
        element.setValidUntil(validUntil);
        element.setVersion(version);
        return element;
    }
}
