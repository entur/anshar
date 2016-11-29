package no.rutebanken.anshar.messages;

import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProductionTimetablesTest {

    @Before
    public void setup() {
        ProductionTimetables.timetableDeliveries.clear();
    }


    @Test
    public void testAddNull() {
        int previousSize = ProductionTimetables.getAll().size();

        ProductionTimetables.add("test", null);

        assertTrue(ProductionTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddJourney() {
        int previousSize = ProductionTimetables.getAll().size();
        ProductionTimetableDeliveryStructure element = createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1));

        ProductionTimetables.add("test", element);

        assertTrue(ProductionTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testExpiredJourney() {
        int previousSize = ProductionTimetables.getAll().size();

        ProductionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().minusMinutes(1))
        );

        ProductionTimetables.timetableDeliveries.removeExpiredElements();
        assertTrue(ProductionTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testGetUpdatesOnly() {

        assertEquals(0, ProductionTimetables.getAll().size());

        ProductionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        ProductionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        ProductionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        // Added 3
        assertEquals(3, ProductionTimetables.getAllUpdates("1234-1234").size());

        ProductionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        //Added one
        assertEquals(1, ProductionTimetables.getAllUpdates("1234-1234").size());


        //None added
        assertEquals(0, ProductionTimetables.getAllUpdates("1234-1234").size());

        //Verify that all elements still exist
        assertEquals(4, ProductionTimetables.getAll().size());
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = ProductionTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        ProductionTimetables.add("test", createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)));
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", ProductionTimetables.getAll().size() == expectedSize);

        ProductionTimetables.add("test", createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)));
        assertTrue("Updating Journey added element.", ProductionTimetables.getAll().size() == expectedSize);

        ProductionTimetables.add("test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)));
        expectedSize++;
        assertTrue("Adding Journey did not add element.", ProductionTimetables.getAll().size() == expectedSize);

        ProductionTimetables.add("test2", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)));
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", ProductionTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", ProductionTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", ProductionTimetables.getAll("test").size() == expectedSize-1);

    }

    @Test
    public void testUpdatedJourneyWrongOrder() {

        String version = UUID.randomUUID().toString();

        ProductionTimetableDeliveryStructure structure_1 = createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1));
        structure_1.setResponseTimestamp(ZonedDateTime.now().plusMinutes(1));
        structure_1.setValidUntil(ZonedDateTime.now().plusDays(10));
        ProductionTimetables.add("test", structure_1);

        assertTrue("Adding Journey did not add element.", ProductionTimetables.getAll().size() == 1);

        ProductionTimetableDeliveryStructure structure_2 = createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1));
        structure_2.setResponseTimestamp(ZonedDateTime.now());
        structure_2.setValidUntil(ZonedDateTime.now().plusDays(20));

        ProductionTimetables.add("test", structure_2);
        assertTrue("Updating Journey added element.", ProductionTimetables.getAll().size() == 1);

        assertTrue("PT has been overwritten by older version", ProductionTimetables.getAll().get(0).getValidUntil().isBefore(ZonedDateTime.now().plusDays(19)));

    }

    private ProductionTimetableDeliveryStructure createProductionTimetableDeliveryStructure(String version, ZonedDateTime validUntil) {
        ProductionTimetableDeliveryStructure element = new ProductionTimetableDeliveryStructure();
        element.setResponseTimestamp(ZonedDateTime.now());
        element.setValidUntil(validUntil);
        element.setVersion(version);
        return element;
    }
}
