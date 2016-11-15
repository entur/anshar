package no.rutebanken.anshar.siri.transformer;

import junit.framework.TestCase;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import org.junit.Before;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StopPlaceRegisterMapperTest extends TestCase {


    @Before
    public void setUp() throws Exception {

        Map<String, String> stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("1234", "NSR:QUAY:11223344");
        stopPlaceMap.put("ABC:StopArea:1234", "NSR:QUAY:11223344");
        stopPlaceMap.put("ABC:StopArea:2345", "NSR:QUAY:22334455");
        stopPlaceMap.put("ABC:StopArea:3456", "NSR:QUAY:33445566");
        stopPlaceMap.put("ABC:StopArea:4567", "NSR:QUAY:44556677");
        stopPlaceMap.put("ABC:StopArea:5678", "NSR:QUAY:55667788");
        stopPlaceMap.put("XYZ:StopArea:5555", "NSR:QUAY:44444444");
        StopPlaceRegisterMapper.setStopPlaceMappings(stopPlaceMap);
    }

    public void testNoPrefixMapping() {

        List<String> prefixes = new ArrayList<>();

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("1234"));
    }

    public void testSimpleMapping() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:11223344", mapper.apply("1234"));
    }

    public void testMultiplePrefixes() {

        List<String> prefixes = new ArrayList<>();
        prefixes.add("ABC");
        prefixes.add("XYZ");

        StopPlaceRegisterMapper mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, prefixes);

        assertEquals("NSR:QUAY:44444444", mapper.apply("5555"));
    }

}