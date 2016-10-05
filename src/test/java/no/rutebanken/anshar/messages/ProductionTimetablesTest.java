package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import org.junit.Test;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class ProductionTimetablesTest {


    @Test
    public void testAddNull() {
        int previousSize = ProductionTimetables.getAll().size();

        ProductionTimetables.add(null, "test");

        assertTrue(ProductionTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddJourney() {
        int previousSize = ProductionTimetables.getAll().size();
        ProductionTimetableDeliveryStructure element = createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1));

        ProductionTimetables.add(element, "test");

        assertTrue(ProductionTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = ProductionTimetables.getAll().size();

        ProductionTimetables.add(
                createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().minusMinutes(1))
                , "test"
        );

        ProductionTimetables.timetableDeliveries.removeExpiredElements();
        assertTrue(ProductionTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = ProductionTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        ProductionTimetables.add(createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)), "test");
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", ProductionTimetables.getAll().size() == expectedSize);

        ProductionTimetables.add(createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)), "test");
        assertTrue("Updating Journey added element.", ProductionTimetables.getAll().size() == expectedSize);

        ProductionTimetables.add(createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)), "test");
        expectedSize++;
        assertTrue("Adding Journey did not add element.", ProductionTimetables.getAll().size() == expectedSize);

        ProductionTimetables.add(createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)), "test2");
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", ProductionTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", ProductionTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", ProductionTimetables.getAll("test").size() == expectedSize-1);

    }

    private ProductionTimetableDeliveryStructure createProductionTimetableDeliveryStructure(String version, ZonedDateTime validUntil) {
        ProductionTimetableDeliveryStructure element = new ProductionTimetableDeliveryStructure();
        element.setValidUntil(validUntil);
        element.setVersion(version);
        return element;
    }
}
