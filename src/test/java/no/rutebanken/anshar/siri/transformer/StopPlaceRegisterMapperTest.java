package no.rutebanken.anshar.siri.transformer;

import junit.framework.TestCase;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import org.junit.Before;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import java.util.HashMap;
import java.util.Map;

public class StopPlaceRegisterMapperTest extends TestCase {

    StopPlaceRegisterMapper mapper;



    @Before
    public void setUp() throws Exception {
        Map<String, String> stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("1234", "11223344");
        stopPlaceMap.put("2345", "22334455");
        stopPlaceMap.put("3456", "33445566");
        stopPlaceMap.put("4567", "44556677");
        stopPlaceMap.put("5678", "55667788");
        mapper = new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, stopPlaceMap);
    }

    public void testSimpleMapping() {
        assertEquals("11223344", mapper.apply("1234"));
    }

}