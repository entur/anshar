package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.App;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class ProductionTimetablesTest {

    @Autowired
    private ProductionTimetables productionTimetables;

    @Before
    public void setup() {

    }


    @Test
    public void testAddNull() {
        int previousSize = productionTimetables.getAll().size();

        productionTimetables.add("test", null);

        assertTrue(productionTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddJourney() {
        int previousSize = productionTimetables.getAll().size();
        ProductionTimetableDeliveryStructure element = createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1));

        productionTimetables.add("test", element);

        assertTrue(productionTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testGetUpdatesOnly() {
        int previousSize = productionTimetables.getAll().size();

        productionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        productionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        productionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        // Added 3
        assertEquals(previousSize+3, productionTimetables.getAllUpdates("1234-1234").size());

        productionTimetables.add(
                "test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        //Added one
        assertEquals(1, productionTimetables.getAllUpdates("1234-1234").size());


        //None added
        assertEquals(0, productionTimetables.getAllUpdates("1234-1234").size());

        //Verify that all elements still exist
        assertEquals(previousSize+4, productionTimetables.getAll().size());
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = productionTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        productionTimetables.add("test", createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)));
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", productionTimetables.getAll().size() == expectedSize);

        productionTimetables.add("test", createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1)));
        assertTrue("Updating Journey added element.", productionTimetables.getAll().size() == expectedSize);

        productionTimetables.add("test", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)));
        expectedSize++;
        assertTrue("Adding Journey did not add element.", productionTimetables.getAll().size() == expectedSize);

        productionTimetables.add("test2", createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1)));
        expectedSize++;
        assertTrue("Adding Journey for other vendor did not add element.", productionTimetables.getAll().size() == expectedSize);
        assertTrue("Getting Journey for vendor did not return correct element-count.", productionTimetables.getAll("test2").size() == previousSize+1);
        assertTrue("Getting Journey for vendor did not return correct element-count.", productionTimetables.getAll("test").size() == expectedSize-1);

    }

    @Test
    public void testUpdatedJourneyWrongOrder() {

        int previousSize = productionTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        ProductionTimetableDeliveryStructure structure_1 = createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1));
        structure_1.setResponseTimestamp(ZonedDateTime.now().plusMinutes(1));
        structure_1.setValidUntil(ZonedDateTime.now().plusDays(10));
        productionTimetables.add("test", structure_1);

        assertEquals("Adding Journey did not add element.", previousSize+1, productionTimetables.getAll().size());

        ProductionTimetableDeliveryStructure structure_2 = createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1));
        structure_2.setResponseTimestamp(ZonedDateTime.now());
        structure_2.setValidUntil(ZonedDateTime.now().plusDays(20));

        productionTimetables.add("test", structure_2);
        assertEquals("Updating Journey added element.", previousSize+1, productionTimetables.getAll().size());

        assertTrue("PT has been overwritten by older version", productionTimetables.getAll().get(0).getValidUntil().isBefore(ZonedDateTime.now().plusDays(19)));

    }

    private ProductionTimetableDeliveryStructure createProductionTimetableDeliveryStructure(String version, ZonedDateTime validUntil) {
        ProductionTimetableDeliveryStructure element = new ProductionTimetableDeliveryStructure();
        element.setResponseTimestamp(ZonedDateTime.now());
        element.setValidUntil(validUntil);
        element.setVersion(version);
        return element;
    }
}
