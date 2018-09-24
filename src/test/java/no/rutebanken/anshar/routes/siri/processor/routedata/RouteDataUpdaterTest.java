package no.rutebanken.anshar.routes.siri.processor.routedata;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@Ignore
public class RouteDataUpdaterTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    public void compareGtfsAndNetexImport() throws Exception {

        logger.info("Starts with PID {}, and waits some time before proceeding", ManagementFactory.getRuntimeMXBean().getName());
        printMemoryDetails();
//        Thread.sleep(10_000);
        printMemoryDetails();

        logger.info("Reads GTFS...");
        NSBGtfsUpdaterService.update("src/test/resources/rb_nsb-aggregated-gtfs.zip",
                "src/test/resources/rb_gjb-aggregated-gtfs.zip",
                "src/test/resources/rb_flt-aggregated-gtfs.zip");

        printMemoryDetails();
//        Thread.sleep(10_000);
        printMemoryDetails();

        logger.info("Reads NeTEx...");
        NetexUpdaterService.update("src/test/resources/rb_nsb-aggregated-netex.zip",
                "src/test/resources/rb_gjb-aggregated-netex.zip",
                "src/test/resources/rb_flt-aggregated-netex.zip",
                "src/test/resources/CurrentAndFuture_latest.zip");
        printMemoryDetails();
//        Thread.sleep(10_000);
        printMemoryDetails();

        Set<String> trainNumbers = NetexUpdaterService.getTrainNumbers();
        logger.info("Checks service journeys for {} different train numbers from NeTEx...", trainNumbers.size());
        ArrayList<String> serviceJourneysDiffers = new ArrayList<>();
        for (String trainNumber : trainNumbers) {
            Set<String> serviceJourneysFromGtfs = NSBGtfsUpdaterService.getServiceJourney(trainNumber);
            Set<String> serviceJourneysFromNetex = NetexUpdaterService.getServiceJourney(trainNumber);
            if (serviceJourneysFromGtfs.size() != serviceJourneysFromNetex.size() || !serviceJourneysFromGtfs.containsAll(serviceJourneysFromNetex)) {
                logger.error("serviceJourneys differs for train number = {}\n  GTFS  : {}\n  NETEX : {}", trainNumber, serviceJourneysFromGtfs, serviceJourneysFromNetex);
                serviceJourneysDiffers.add(trainNumber);
            }
        }
        assertTrue("The stoptimes from GTFS differs the ones for NeTEx for " + serviceJourneysDiffers.size() + " service journeys", serviceJourneysDiffers.isEmpty());
        logger.info("...and the GTFS and NeTEx services had the same service journeys for these train numbers!");

        Set<String> serviceJourneys = NetexUpdaterService.getServiceJourneys();
        logger.info("Checks stoptimes for {} different service journeys from NeTEx...", serviceJourneys.size());
        ArrayList<String> stoptimesDiffers = new ArrayList<>();
        for (String serviceJourney : serviceJourneys) {
            List<StopTime> stopsFromGtfs = NSBGtfsUpdaterService.getStopTimes(serviceJourney);
            List<StopTime> stopsFromNetex = NetexUpdaterService.getStopTimes(serviceJourney);
            if (stopsFromGtfs.size() != stopsFromNetex.size() || !stopsFromGtfs.containsAll(stopsFromNetex)) {
                logger.error("Stop times differs for serviceJourneyId={}\n  GTFS  : {}\n  NETEX : {}", serviceJourney, stopsFromGtfs, stopsFromNetex);
                stoptimesDiffers.add(serviceJourney);
            }
        }
        assertTrue("The stop times from GTFS differs the ones for NeTEx for " + stoptimesDiffers.size() + " service journeys", stoptimesDiffers.isEmpty());
        logger.info("...and the GTFS and NeTEx services had the same stop times for these service journeys!");


        logger.info("Checks service dates for {} different service journeys from NeTEx...", serviceJourneys.size());
        ArrayList<String> servicedatesDiffers = new ArrayList<>();
        for (String serviceJourney : serviceJourneys) {
            List<ServiceDate> fromGtfs = NSBGtfsUpdaterService.getServiceDates(serviceJourney);
            List<ServiceDate> fromNetex = NetexUpdaterService.getServiceDates(serviceJourney);
            if (fromGtfs.size() != fromNetex.size() || !fromGtfs.containsAll(fromNetex)) {
                logger.error("Service dates differs for serviceJourneyId={}\n  GTFS  : {}\n  NETEX : {}", serviceJourney, fromGtfs, fromNetex);
                servicedatesDiffers.add(serviceJourney);
            }
        }
        assertTrue("The service dates from GTFS differs the ones for NeTEx for " + servicedatesDiffers.size() + " service journeys", servicedatesDiffers.isEmpty());
        logger.info("...and the GTFS and NeTEx services had the same service dates for these service journeys!");

        logger.info("Checks that the NeTEx service has all the parent stops the GTFS service has (allow netex to have more as they have different sources)");
        Map<String, String> fromGtfs = NSBGtfsUpdaterService.getParentStops();
        logger.info("There are {} mappings from gtfs, {} regarding quays", fromGtfs.size(), fromGtfs.keySet().stream().filter(s -> s.startsWith("NSR:Quay:")).collect(Collectors.toList()).size());
        Map<String, String> fromNetex = NetexUpdaterService.getParentStops();
        ArrayList<String> inNetexNotGtfs = getFromSecondSetNotInFirst(fromGtfs.keySet(), fromNetex.keySet());
        logger.info("These {} KEYS are from NeTEx but not found in GTFS: {}", inNetexNotGtfs.size(), inNetexNotGtfs);
        ArrayList<String> inGtfsNotNetex = getFromSecondSetNotInFirst(fromNetex.keySet(), fromGtfs.keySet());
        logger.info("These {} KEYS are from GTFS but not found in NeTEx: {}", inGtfsNotNetex.size(), inGtfsNotNetex);

        ArrayList<String> valuesInNetexNotGtfs = getFromSecondSetNotInFirst(new HashSet<>(fromGtfs.values()), new HashSet<>(fromNetex.values()));
        logger.info("These {} VALUES are from NeTEx but not found in GTFS: {}", valuesInNetexNotGtfs.size(), valuesInNetexNotGtfs);
        ArrayList<String> valuesInGtfsNotNetex = getFromSecondSetNotInFirst(new HashSet<>(fromNetex.values()), new HashSet<>(fromGtfs.values()));
        logger.info("These {} VALUES are from GTFS but not found in NeTEx: {}", valuesInGtfsNotNetex.size(), valuesInGtfsNotNetex);

        assertTrue("There are more stops from gtfs (" + fromGtfs.size() + ") than from netex (" + fromNetex.size() + ")", fromNetex.size() >= fromGtfs.size());

        printMemoryDetails();
        printMemoryDetails();

    }

    private void printMemoryDetails() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        logger.error("Memory details: {}% free, total={}, free={}, used={}", Math.round(100 * free / total), total, free, (total - free));

        rt.gc();
    }

    private ArrayList<String> getFromSecondSetNotInFirst(Set<String> first, Set<String> second) {
        ArrayList<String> keys = new ArrayList<>();
        for (String key : second) {
            if (!first.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

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
