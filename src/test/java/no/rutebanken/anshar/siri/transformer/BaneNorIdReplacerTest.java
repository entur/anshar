package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.impl.BaneNorIdReplacer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class BaneNorIdReplacerTest {

    Map<String, String> stopPlaceMap;

    @Before
    public void setUp() throws Exception {

        stopPlaceMap = new HashMap<>();
        stopPlaceMap.put("OSL", "NSR:StopArea:11223344");
        stopPlaceMap.put("NAT", "NSR:StopArea:22334455");
        Object mappings = ApplicationContextHolder.getContext().getBean("getStopPlaceMappings");
        if (mappings instanceof Map) {
            //Manually adding custom mapping to Spring context
            ((Map) mappings).putAll(stopPlaceMap);
        }
    }

    @Test
    public void testSimpleMapping() {

        BaneNorIdReplacer mapper = new BaneNorIdReplacer(JourneyPlaceRefStructure.class);
        mapper.addStopPlaceMappings(stopPlaceMap);

        assertEquals("NSR:StopArea:11223344", mapper.apply("OSL"));
    }

}
