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

package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.JourneyPlaceRefStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StopPlaceRegisterMapperTest extends SpringBootBaseTest {

    private Map<String, String> stopPlaceMap;
    private Set<String> stopQuays;

    @BeforeEach
    public void setUp() throws Exception {

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("1234", "NSR:Quay:11223344");
        stopPlaceMap.put("ABC:Quay:1234", "NSR:Quay:11223344");
        stopPlaceMap.put("ABC:Quay:2345", "NSR:Quay:22334455");
        stopPlaceMap.put("ABC:Quay:3456", "NSR:Quay:33445566");
        stopPlaceMap.put("ABC:Quay:4567", "NSR:Quay:44556677");
        stopPlaceMap.put("ABC:Quay:5678", "NSR:Quay:55667788");
        stopPlaceMap.put("XYZ:Quay:5555", "NSR:Quay:44444444");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        stopQuays = new HashSet<>();
        stopQuays.addAll(stopPlaceMap.values());
        stopQuays.add("NSR:StopPlace:1");
        stopQuays.add("NSR:Quay:1");
        stopQuays.add("NSR:Quay:2");
        stopQuays.add("NSR:Quay:3");
        stopPlaceService.addStopQuays(stopQuays);
    }

    @Test
    public void testNoPrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:Quay:11223344", mapper.apply("1234"));
    }

    @Test
    public void testSimpleMapping() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:Quay:11223344", mapper.apply("1234"));
    }

    @Test
    public void testMultiplePrefixes() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");
        prefixes.add("XYZ");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:Quay:44444444", mapper.apply("5555"));
    }


    @Test
    public void testDuplicatePrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, "TST",JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:Quay:11223344", mapper.apply("NSR:Quay:11223344"));
    }

    @Test
    public void testUnmappedThenMapped() {

        stopPlaceMap = new HashMap<>();

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        //Manually adding custom mapping to Spring context
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);


        HealthManager healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);

        List<String> prefixes = new ArrayList<>();

        String datasetId = "TST_" + System.currentTimeMillis();
        String originalId = "4321";
        String mappedId = "NSR:Quay:44332211";

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(SiriDataType.VEHICLE_MONITORING, datasetId,JourneyPlaceRefStructure.class, prefixes);

        assertEquals(originalId, mapper.apply(originalId));

        Map<SiriDataType, Set<String>> unmappedIds = healthManager.getUnmappedIds(datasetId);

        assertEquals(1, unmappedIds.size());

        Set<String> ids = unmappedIds.get(SiriDataType.VEHICLE_MONITORING);
        assertEquals(1, ids.size());
        assertEquals(originalId, ids.iterator().next());


        //Add new mapping-value
        stopPlaceMap.put(originalId, mappedId);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        assertEquals(mappedId, mapper.apply(originalId));

        unmappedIds = healthManager.getUnmappedIds(datasetId);
        assertEquals(1, unmappedIds.size());
        ids = unmappedIds.get(SiriDataType.VEHICLE_MONITORING);
        assertEquals(0, ids.size());

    }

    @Test
    public void testValidNsrId() {
        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        String validId = (String) stopQuays.toArray()[0];

        for (String id : stopQuays) {
            assertTrue(stopPlaceService.isKnownId(id));
        }
        final String unknownId = "NSR:Quay:9";

        assertFalse(stopQuays.contains(unknownId));
        assertFalse(stopPlaceService.isKnownId(unknownId));
    }
}