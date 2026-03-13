/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.data;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.FacilityRef;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;

import java.time.ZonedDateTime;

import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FacilitiesTest extends SpringBootBaseTest {

    @Autowired
    private Facilities facilities;

    @BeforeEach
    public void init() {
        facilities.clearAll();
    }


    @Test
    public void testAddSituation() {
        int previousSize = facilities.getAll().size();
        FacilityConditionStructure element = createFacilityMonitoring(
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusHours(4),
                "NSR:Parking:1234");

        facilities.add("test", element);

        assertEquals(previousSize + 1, facilities.getAll().size(), "Situation not added");
    }

    @Test
    public void testAddNullSituation() {
        int previousSize = facilities.getAll().size();
        facilities.add("test", null);

        assertEquals(previousSize, facilities.getAll().size(), "Null-situation added");
    }

    @Test
    public void testUpdatedFacilityCondition() {
        int previousSize = facilities.getAll().size();

        //Add
        FacilityConditionStructure element = createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:1");
        facilities.add("test", element);
        int expectedSize = previousSize+1;
        assertEquals(expectedSize, facilities.getAll().size());

        //Update
        FacilityConditionStructure element2 = createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:1");
        facilities.add("test", element2);
        assertEquals(expectedSize, facilities.getAll().size());

        //Add
        FacilityConditionStructure element3 = createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:3");
        facilities.add("test", element3);
        expectedSize++;
        assertEquals(expectedSize, facilities.getAll().size());

        //Add
        FacilityConditionStructure element4 = createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:4");
        facilities.add("test", element4);
        expectedSize++;
        assertEquals(expectedSize, facilities.getAll().size());

        //Add
        FacilityConditionStructure element5 = createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:5");
        facilities.add("test2", element5);
        expectedSize++;
        assertEquals(expectedSize, facilities.getAll().size());

        assertEquals(facilities.getAll("test2").size(), previousSize + 1);
        assertEquals(facilities.getAll("test").size(), expectedSize - 1);
    }

    @Test
    public void testGetUpdatesOnly() {

        int previousSize = facilities.getAll().size();

        facilities.add("test", createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:1"));
        facilities.add("test", createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:2"));
        facilities.add("test", createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:3"));

        sleep(50);

        // Added 3
        assertEquals(previousSize+3, facilities.getAllUpdates("1234-1234", null).size());

        facilities.add("test", createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:4"));

        sleep(50);

        //Added one
        assertEquals(1, facilities.getAllUpdates("1234-1234", null).size());
        sleep(50);

        //None added
        assertEquals(0, facilities.getAllUpdates("1234-1234", null).size());
        sleep(50);
        //Verify that all elements still exist
        assertEquals(previousSize+4, facilities.getAll().size());
    }

    @Test
    public void testGetUpdatesOnlyFromCache() {

        int previousSize = facilities.getAll().size();

        String datasetId = "cache-fm-datasetid";

        facilities.add(datasetId, createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:1"));
        facilities.add(datasetId, createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:2"));
        facilities.add(datasetId, createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:3"));

        sleep(50);
        // Added 3
        assertEquals(previousSize+3, facilities.getAllCachedUpdates("1234-1234-cache", datasetId,
            null
        ).size());

        facilities.add(datasetId, createFacilityMonitoring(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1), "NSR:Parking:4"));

        sleep(50);

        //Added one
        assertEquals(1, facilities.getAllCachedUpdates("1234-1234-cache", datasetId, null).size());
        sleep(50);

        //None added
        assertEquals(0, facilities.getAllCachedUpdates("1234-1234-cache", datasetId, null).size());
        sleep(50);
        //Verify that all elements still exist
        assertEquals(previousSize+4, facilities.getAll().size());
    }

    private FacilityConditionStructure createFacilityMonitoring(
            ZonedDateTime startTime,
            ZonedDateTime endTime, String facilityRef
    ) {
        FacilityConditionStructure element = new FacilityConditionStructure();
        FacilityRef facRef = new FacilityRef();
        facRef.setValue(facilityRef);
        element.setFacilityRef(facRef);

        element.setValidityPeriod(new HalfOpenTimestampOutputRangeStructure());
        element.getValidityPeriod().setStartTime(startTime);
        element.getValidityPeriod().setEndTime(endTime);
        return element;
    }
}
