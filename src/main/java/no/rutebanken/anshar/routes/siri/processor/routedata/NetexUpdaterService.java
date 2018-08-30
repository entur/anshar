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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"unchecked", "Duplicates"})
public class NetexUpdaterService {

    private static final Logger logger = LoggerFactory.getLogger(NetexUpdaterService.class);

    private static final int UPDATE_FREQUENCY = 6;
    private static final TimeUnit FREQUENCY_TIME_UNIT = TimeUnit.HOURS;

    // Kept non-configurable since this whole adapter is a temporary hack - ROR-326/ROR-329
    private static final String NETEX_NSB_URL = "https://storage.googleapis.com/marduk-production/outbound/netex/rb_nsb-aggregated-netex.zip";
    private static final String NETEX_GJB_URL = "https://storage.googleapis.com/marduk-production/outbound/netex/rb_gjb-aggregated-netex.zip";
    private static final String NETEX_FLT_URL = "https://storage.googleapis.com/marduk-production/outbound/netex/rb_flt-aggregated-netex.zip";
    private static final String STOPPLACE_URL = "https://storage.googleapis.com/marduk-production/tiamat/CurrentAndFuture_latest.zip";

    private static Map<String, List<StopTime>> tripStops = new HashMap<>();
    private static Map<String, Set<String>> trainNumberTrips = new HashMap<>();
    private static Map<String, List<ServiceDate>> tripDates = new HashMap<>();
    private static Map<String, String> parentStops = new HashMap<>();

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

    public static boolean isKnownTrainNr(String trainNumber) {
        return trainNumberTrips.containsKey(trainNumber);
    }

    static Set<String> getServiceJourneys() {
        return tripStops.keySet();
    }

    static Set<String> getTrainNumbers() {
        return trainNumberTrips.keySet();
    }

    synchronized static void initializeUpdater() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> {
                    long t1 = System.currentTimeMillis();
                    logger.info("Updating NeTEx-data - start");

                    String path_nsb = null;
                    String path_flt = null;
                    String path_gjb = null;
                    String path_stops = null;
                    try {
                        path_nsb = readUrl(NETEX_NSB_URL);
                        path_flt = readUrl(NETEX_FLT_URL);
                        path_gjb = readUrl(NETEX_GJB_URL);
                        path_stops = readUrl(STOPPLACE_URL);

                        if (path_nsb != null && path_flt != null && path_gjb != null && path_stops != null) {
                            update(path_nsb, path_flt, path_gjb, path_stops);
                        } else {
                            logger.error("Do not update NeTEx data as some files could not be downloaded: nsb={}, flt={}, gjb={}, stops={}", path_nsb, path_flt, path_gjb, path_stops);
                        }
                    } finally {
                        cleanup(path_nsb, path_flt, path_gjb, path_stops);
                    }
                    logger.info("Updating NeTEx-data - done: {} ms", (System.currentTimeMillis() - t1));
                },
                0,
                UPDATE_FREQUENCY,
                FREQUENCY_TIME_UNIT);
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
        long start = System.currentTimeMillis();
        Map<String, List<StopTime>> tmpTripStops = new HashMap<>();
        Map<String, Set<String>> tmpTrainNumberTrips = new HashMap<>();
        Map<String, List<ServiceDate>> tmpTripDates = new HashMap<>();
        Map<String, String> tmpParentStops = new HashMap<>();

        for (String path : paths) {
            readNeTEx(path, tmpTripStops, tmpTrainNumberTrips, tmpTripDates, tmpParentStops);
        }

        // Swapping updated data
        tripStops = tmpTripStops;
        trainNumberTrips = tmpTrainNumberTrips;
        tripDates = tmpTripDates;
        parentStops = tmpParentStops;
        logger.info("Read and merged {} NeTEx files in {} ms", paths.length, (System.currentTimeMillis()-start));
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
            byte data[] = new byte[1024];
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

    private static void readNeTEx(String path, Map<String, List<StopTime>> tripStops, Map<String, Set<String>> trainNumberTrips,
                                  Map<String, List<ServiceDate>> tripDates, Map<String, String> parentStops) {
        try {

            NetexProcessor netexProcessor = new NetexProcessor();
            netexProcessor.loadFiles(new File(path));
            tripStops.putAll(netexProcessor.getTripStops());
            trainNumberTrips.putAll(netexProcessor.getTrainNumberTrips());
            tripDates.putAll(netexProcessor.getTripDates());
            parentStops.putAll(netexProcessor.getParentStops());
        } catch (IOException e) {
            logger.error("Could not load NeTEx file from path {}", path);
        }
    }



}
