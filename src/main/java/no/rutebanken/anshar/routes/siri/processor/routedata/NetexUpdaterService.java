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

package no.rutebanken.anshar.routes.siri.processor.routedata;

import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.OperatingDay;
import org.rutebanken.netex.model.ServiceAlterationEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"unchecked", "Duplicates"})
@Service
@Configuration
public class NetexUpdaterService {

    private static final Logger logger = LoggerFactory.getLogger(NetexUpdaterService.class);

    private static final int UPDATE_FREQUENCY = 6;
    private static final TimeUnit FREQUENCY_TIME_UNIT = TimeUnit.HOURS;

    //Initial NeTEX-loading is async by default
    @Value("${anshar.startup.wait.for.netex.initialization:false}")
    private boolean delayStartupForInitialization;

    //NeTEX-loading is enabled by default
    @Value("${anshar.startup.load.mapping.data:true}")
    private boolean loadMappingData;

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Kept non-configurable since this whole adapter is a temporary hack - ROR-326/ROR-329
    private static final String[] urls = {
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_nsb-aggregated-netex.zip", // NSB
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_gjb-aggregated-netex.zip", // Gjøvikbanen
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_flt-aggregated-netex.zip", // Flytoget
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_flb-aggregated-netex.zip", // Flåmsbana
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_goa-aggregated-netex.zip", // Go-Ahead
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_sjn-aggregated-netex.zip", // SJ
        "https://storage.googleapis.com/marduk-production/outbound/netex/rb_vyg-aggregated-netex.zip", // VYG
        "https://storage.googleapis.com/marduk-production/tiamat/CurrentAndFuture_latest.zip"
    };

    private static Map<String, List<StopTime>> tripStops = new HashMap<>();
    private static Map<String, Set<String>> trainNumberTrips = new HashMap<>();
    private static Map<String, List<ServiceDate>> tripDates = new HashMap<>();
    private static Map<String, String> parentStops = new HashMap<>();
    private static Map<String, String> quayPublicCodes = new HashMap<>();
    private static Map<String, List<DatedServiceJourney>> datedServiceJourneysForTrip = new HashMap<>();
    private static Map<String, OperatingDay> operatingDayRefs = new HashMap<>();

    //public for testing-purposes
    public static Map<String, LocationStructure> locations = new HashMap<>();
    public static Map<String, AllVehicleModesOfTransportEnumeration> modes = new HashMap<>();

    public static boolean isStopIdOrParentMatch(String stop1, String stop2) {
        return stop1.equals(stop2) || parentStops.get(stop2).equals(parentStops.get(stop1));
    }

    static Map<String, String> getParentStops() {
        return parentStops;
    }

    public static List<StopTime> getStopTimes(String serviceJourneyId) {
        return tripStops.get(serviceJourneyId);
    }

    public static List<ServiceDate> getServiceDates(String serviceJourneyId) {
        return tripDates.get(serviceJourneyId);
    }

    public static Set<String> getServiceJourney(String trainNumber) {
        return trainNumberTrips.get(trainNumber);
    }

    public static String getPublicCode(String quayRef) {
        if (quayRef != null) {
            return quayPublicCodes.get(quayRef);
        }
        return null;
    }

    public static boolean isKnownTrainNr(String trainNumber) {
        return trainNumberTrips.containsKey(trainNumber);
    }

    public static boolean isDsjCancelled(String serviceJourneyId, ServiceDate serviceDate) {
        if (serviceJourneyId != null) {
            if (datedServiceJourneysForTrip.containsKey(serviceJourneyId)) {
                List<DatedServiceJourney> datedServiceJourneys = datedServiceJourneysForTrip.get(serviceJourneyId);
                for (DatedServiceJourney dsj : datedServiceJourneys) {

                    OperatingDay operatingDay = operatingDayRefs.get(dsj.getOperatingDayRef().getRef());
                    if (isSameDate(operatingDay, serviceDate)) {
                        return dsj.getServiceAlteration() != null && (
                                dsj.getServiceAlteration() == ServiceAlterationEnumeration.CANCELLATION ||
                                dsj.getServiceAlteration() == ServiceAlterationEnumeration.REPLACED
                                );
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSameDate(OperatingDay operatingDay, ServiceDate serviceDate) {
        return (
                operatingDay.getCalendarDate().getYear() == serviceDate.year &&
                operatingDay.getCalendarDate().getMonthValue() == serviceDate.month &&
                operatingDay.getCalendarDate().getDayOfMonth() == serviceDate.day);
    }

    public static boolean serviceJourneyIdExists(String serviceJourneyId) {
        return serviceJourneyId != null && tripStops.containsKey(serviceJourneyId);
    }

    @PostConstruct
    synchronized void initializeUpdater() {
        if (!loadMappingData) {
            logger.info("Loading NeTEx disabled.");
            return;
        }

        logger.info("Starting the NeTEx updater service");
        int initialDelay = 0;

        if (delayStartupForInitialization) {
            //Initialize data synchronous
            logger.info("Loading NeTEx before continuing.");
            initializeNetexData();
            initialDelay = UPDATE_FREQUENCY;
        }

        executor.scheduleWithFixedDelay(() -> initializeNetexData(),
                initialDelay,
                UPDATE_FREQUENCY,
                FREQUENCY_TIME_UNIT);

    }

    private static void initializeNetexData() {
        long t1 = System.currentTimeMillis();
        logger.info("Updating NeTEx-data - start");

        String[] paths = new String[urls.length];

        try {
            for (int i = 0; i < urls.length; i++) {
                logger.info("Downloading {}", urls[i]);
                paths[i] = readUrl(urls[i]);
                if (paths[i] == null) {
                    logger.error("File could not be downloaded - retrying: {}", urls[i]);
                    i--; // trigger retry
                }
            }

             update(paths);

        } finally {
            cleanup(paths);
        }
        logger.info("Updating NeTEx-data - done: {} ms", (System.currentTimeMillis() - t1));
    }

    private static void cleanup(String... paths) {
        for (String path : paths) {
            if (path != null) {
                try {
                    logger.debug("Deletes temporary file {} ", path);
                    File file = new File(path);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                } catch (Exception e) {
                    logger.warn("Could not delete file {}", path, e);
                }
            }
        }
    }

    public static void update(String... paths) {
        logger.info("Reading {} NeTEx files", paths.length);
        long start = System.currentTimeMillis();
        Map<String, List<StopTime>> tmpTripStops = new HashMap<>();
        Map<String, Set<String>> tmpTrainNumberTrips = new HashMap<>();
        Map<String, List<DatedServiceJourney>> tmpDatedServiceJourneysForTrip = new HashMap<>();
        Map<String, List<ServiceDate>> tmpTripDates = new HashMap<>();
        Map<String, String> tmpParentStops = new HashMap<>();
        Map<String, String> tmpQuayPublicCodes = new HashMap<>();
        Map<String, LocationStructure> tmpLocations = new HashMap<>();
        Map<String, AllVehicleModesOfTransportEnumeration> tmpModes = new HashMap<>();
        Map<String, OperatingDay> tmpOperatingDayRefs = new HashMap<>();

        for (String path : paths) {
            readNeTEx(path, tmpTripStops, tmpTrainNumberTrips, tmpDatedServiceJourneysForTrip, tmpTripDates, tmpParentStops, tmpQuayPublicCodes,
                tmpLocations, tmpModes, tmpOperatingDayRefs);
        }

        // Swapping updated data
        tripStops = tmpTripStops;
        trainNumberTrips = tmpTrainNumberTrips;
        datedServiceJourneysForTrip = tmpDatedServiceJourneysForTrip;
        tripDates = tmpTripDates;
        parentStops = tmpParentStops;
        quayPublicCodes = tmpQuayPublicCodes;
        locations = tmpLocations;
        modes = tmpModes;
        operatingDayRefs = tmpOperatingDayRefs;
        logger.info("Read and merged {} NeTEx files in {} ms", paths.length, (System.currentTimeMillis()-start));
    }

    private static String readUrl(String url) {

        InputStream httpIn = null;
        OutputStream bufferedOut = null;
        try {
            long start = System.currentTimeMillis();
            File tmpFile = File.createTempFile("netex", ".zip");
            // check the http connection before we do anything to the fs
            httpIn = new BufferedInputStream(new URL(url).openStream());
            bufferedOut = new BufferedOutputStream(new FileOutputStream(tmpFile), 1024);
            byte[] data = new byte[1024];
            boolean fileComplete = false;
            int count;
            while (!fileComplete) {
                count = httpIn.read(data, 0, 1024);
                if (count <= 0) {
                    fileComplete = true;
                } else {
                    bufferedOut.write(data, 0, count);
                }
            }
            logger.debug("Downloaded {} to temporary file {} in {} ms", url, tmpFile.getAbsolutePath(), (System.currentTimeMillis() - start));
            return tmpFile.getAbsolutePath();
        } catch (IOException e) {
            logger.error("Could not download file", e);
        } finally {
            try {
                if (httpIn != null) httpIn.close();
            } catch (Exception e) {
                logger.error("Could not close resource", e);
            }
            try {
                if (bufferedOut != null) bufferedOut.close();
            } catch (Exception e) {
                logger.error("Could not close resource", e);
            }
        }
        return null;
    }

    private static void readNeTEx(
            String path, Map<String, List<StopTime>> tripStops,
            Map<String, Set<String>> trainNumberTrips,
            Map<String, List<DatedServiceJourney>> datedServiceJourneysForTrip,
            Map<String, List<ServiceDate>> tripDates,
            Map<String, String> parentStops, Map<String, String> quayPublicCodes,
            Map<String, LocationStructure> locations, Map<String, AllVehicleModesOfTransportEnumeration> tmpModes,
            Map<String, OperatingDay> operatingDayRefs) {
        try {

            NetexParserProcessor netexProcessor = new NetexParserProcessor();
            netexProcessor.loadFiles(new File(path));
            tripStops.putAll(netexProcessor.getTripStops());

            for (String trainNumber : netexProcessor.getTrainNumberTrips().keySet()) {
                if (!trainNumberTrips.containsKey(trainNumber)) {
                    trainNumberTrips.put(trainNumber, netexProcessor.getTrainNumberTrips().get(trainNumber));
                } else {
                    trainNumberTrips.get(trainNumber).addAll(netexProcessor.getTrainNumberTrips().get(trainNumber));
                }
            }

            datedServiceJourneysForTrip.putAll(netexProcessor.getDatedServiceJourneyForServiceJourneyId());
            operatingDayRefs.putAll(netexProcessor.getOperatingDayRefs());
            tripDates.putAll(netexProcessor.getTripDates());
            parentStops.putAll(netexProcessor.getParentStops());
            quayPublicCodes.putAll(netexProcessor.getPublicCodeByQuayId());
            locations.putAll(netexProcessor.getLocations());
            tmpModes.putAll(netexProcessor.getModes());
        } catch (IOException e) {
            logger.error("Could not load NeTEx file from path {}", path);
        }
    }



}
