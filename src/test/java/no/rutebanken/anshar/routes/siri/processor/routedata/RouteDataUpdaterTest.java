package no.rutebanken.anshar.routes.siri.processor.routedata;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Disabled
public class RouteDataUpdaterTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    public void testStopPlaces() {
        NetexUpdaterService.update("src/test/resources/CurrentAndFuture_latest.zip");
        Map<String, String> parentStops = NetexUpdaterService.getParentStops();
        assertEquals(100676, parentStops.size());
        logger.info("Got {} stopmappings", parentStops.size());
        HashSet<String> uniqueParents = new HashSet<>(parentStops.values());
        assertEquals(57763, uniqueParents.size());
        logger.info("With {} uniqe parent stops", uniqueParents.size());
    }

}
