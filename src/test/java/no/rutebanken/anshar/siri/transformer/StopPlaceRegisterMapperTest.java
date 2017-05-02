package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceUpdaterService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class StopPlaceRegisterMapperTest {

    Map<String, String> stopPlaceMap;

    @Before
    public void setUp() throws Exception {

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("1234", "NSR:QUAY:11223344");
        stopPlaceMap.put("ABC:Quay:1234", "NSR:QUAY:11223344");
        stopPlaceMap.put("ABC:Quay:2345", "NSR:QUAY:22334455");
        stopPlaceMap.put("ABC:Quay:3456", "NSR:QUAY:33445566");
        stopPlaceMap.put("ABC:Quay:4567", "NSR:QUAY:44556677");
        stopPlaceMap.put("ABC:Quay:5678", "NSR:QUAY:55667788");
        stopPlaceMap.put("XYZ:Quay:5555", "NSR:QUAY:44444444");

        Object mappings = ApplicationContextHolder.getContext().getBean("getStopPlaceMappings");
        if (mappings instanceof Map) {
            //Manually adding custom mapping to Spring context
            ((Map) mappings).putAll(stopPlaceMap);
        }
    }

    @Test
    public void testNoPrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("1234"));
    }

    @Test
    public void testSimpleMapping() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("1234"));
    }

    @Test
    public void testMultiplePrefixes() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");
        prefixes.add("XYZ");

        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        stopPlaceService.addStopPlaceMappings(stopPlaceMap);

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:44444444", mapper.apply("5555"));
    }


    @Test
    public void testDuplicatePrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("NSR:QUAY:11223344"));
    }
}