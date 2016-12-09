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
import java.util.List;
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

        String datasetId = "test-add-null";
        productionTimetables.add(datasetId, null);

        assertTrue(productionTimetables.getAll().size() == previousSize);
    }

    @Test
    public void testAddPtElement() {
        int previousSize = productionTimetables.getAll().size();
        ProductionTimetableDeliveryStructure element = createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1));

        String datasetId = "test-add";
        productionTimetables.add(datasetId, element);

        assertTrue(productionTimetables.getAll().size() == previousSize+1);
    }

    @Test
    public void testGetUpdatesOnly() {
        int previousSize = productionTimetables.getAll().size();

        String datasetId = "test-updates-only";
        productionTimetables.add(
                datasetId, createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        productionTimetables.add(
                datasetId, createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        productionTimetables.add(
                datasetId, createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        // Added 3
        assertEquals(previousSize+3, productionTimetables.getAllUpdates("1234-1234", null).size());

        productionTimetables.add(
                datasetId, createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(1))
        );
        //Added one
        assertEquals(1, productionTimetables.getAllUpdates("1234-1234", null).size());


        //None added
        assertEquals(0, productionTimetables.getAllUpdates("1234-1234", null).size());

        //Verify that all elements still exist
        assertEquals(previousSize+4, productionTimetables.getAll().size());
    }

    @Test
    public void testUpdatedPtDelivery() {
        int previousSize = productionTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        //Adding PT
        String datasetId_1 = "test-update";
        productionTimetables.add(datasetId_1, createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(10)));
        int expectedSizeDataset_1 = 1;
        assertEquals("Adding Journey did not add element.", previousSize + expectedSizeDataset_1, productionTimetables.getAll().size());

        //Updating previous PT
        productionTimetables.add(datasetId_1, createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(10)));
        assertEquals("Updating Journey added element.", previousSize + expectedSizeDataset_1, productionTimetables.getAll().size());

        //Adding another PT
        productionTimetables.add(datasetId_1, createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(10)));
        expectedSizeDataset_1++;
        assertEquals("Adding Journey did not add element.", previousSize + expectedSizeDataset_1, productionTimetables.getAll().size());

        //Adding another PT for other dataset
        String datasetId_2 = "test2-update";
        productionTimetables.add(datasetId_2, createProductionTimetableDeliveryStructure(UUID.randomUUID().toString(), ZonedDateTime.now().plusMinutes(10)));
        int expectedSizeDataset_2 = 1;

        assertEquals("Adding Journey for other vendor did not add element.", previousSize + expectedSizeDataset_1 + expectedSizeDataset_2, productionTimetables.getAll().size());
        assertEquals("Getting Journey for vendor did not return correct element-count.", expectedSizeDataset_1, productionTimetables.getAll(datasetId_1).size());
        assertEquals("Getting Journey for vendor did not return correct element-count.", expectedSizeDataset_2, productionTimetables.getAll(datasetId_2).size());

    }

    @Test
    public void testUpdatedPtDeliveryWrongOrder() {

        int previousSize = productionTimetables.getAll().size();
        String version = UUID.randomUUID().toString();

        ProductionTimetableDeliveryStructure structure_1 = createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1));
        structure_1.setResponseTimestamp(ZonedDateTime.now().plusMinutes(1));
        structure_1.setValidUntil(ZonedDateTime.now().plusDays(10));
        String datasetId = "test-wrong-order";
        productionTimetables.add(datasetId, structure_1);

        assertEquals("Adding Journey did not add element.", previousSize + 1, productionTimetables.getAll().size());

        ProductionTimetableDeliveryStructure structure_2 = createProductionTimetableDeliveryStructure(version, ZonedDateTime.now().plusMinutes(1));
        structure_2.setResponseTimestamp(ZonedDateTime.now());
        structure_2.setValidUntil(ZonedDateTime.now().plusDays(20));

        productionTimetables.add(datasetId, structure_2);
        assertEquals("Updating Journey added element.", previousSize + 1, productionTimetables.getAll().size());

        boolean checkedMatchingElement = false;
        List<ProductionTimetableDeliveryStructure> all = productionTimetables.getAll();
        for (ProductionTimetableDeliveryStructure element : all) {
            if (version.equals(element.getVersion())) {
                assertTrue("PT has been overwritten by older version", element.getValidUntil().isBefore(ZonedDateTime.now().plusDays(19)));
                checkedMatchingElement = true;
            }
        }
        assertTrue("Did not check matching ProductionTimetableDeliveryStructure", checkedMatchingElement);

    }

    private ProductionTimetableDeliveryStructure createProductionTimetableDeliveryStructure(String version, ZonedDateTime validUntil) {
        ProductionTimetableDeliveryStructure element = new ProductionTimetableDeliveryStructure();
        element.setResponseTimestamp(ZonedDateTime.now());
        element.setValidUntil(validUntil);
        element.setVersion(version);
        return element;
    }
}
