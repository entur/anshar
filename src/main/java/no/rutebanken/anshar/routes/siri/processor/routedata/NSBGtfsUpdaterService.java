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

import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "Duplicates"})
public class NSBGtfsUpdaterService {

    private static final Logger logger = LoggerFactory.getLogger(NSBGtfsUpdaterService.class);

    private static final int UPDATE_FREQUENCY = 6;
    private static final TimeUnit FREQUENCY_TIME_UNIT = TimeUnit.HOURS;

    // Kept non-configurable since this whole adapter is a temporary hack - ROR-326/ROR-329
    private static final String GTFS_NSB_URL = "https://storage.googleapis.com/marduk-production/outbound/gtfs/rb_nsb-aggregated-gtfs.zip";
    private static final String GTFS_GJB_URL = "https://storage.googleapis.com/marduk-production/outbound/gtfs/rb_gjb-aggregated-gtfs.zip";
    private static final String GTFS_FLT_URL = "https://storage.googleapis.com/marduk-production/outbound/gtfs/rb_flt-aggregated-gtfs.zip";

    private static Map<String, List<StopTime>> tripStops;
    private static Map<String, Set<String>> trainNumberTrips;
    private static Map<String, List<ServiceDate>> tripDates;
    private static Map<String, String> parentStops;

    public static boolean isStopIdOrParentMatch(String stopId, String gtfsStopId) {
        return stopId.equals(gtfsStopId) || parentStops.get(gtfsStopId).equals(parentStops.get(stopId));
    }

    public static Map<String, String> getParentStops() {
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

    synchronized static void initializeUpdater() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> {
                    long t1 = System.currentTimeMillis();
                    logger.info("Updating GTFS-data - start");

                    String path_nsb = null;
                    String path_flt = null;
                    String path_gjb = null;
                    try {
                        path_nsb = readUrl(GTFS_NSB_URL);
                        path_flt = readUrl(GTFS_FLT_URL);
                        path_gjb = readUrl(GTFS_GJB_URL);
                        if (path_nsb != null && path_flt != null && path_gjb != null) {
                            update(path_nsb, path_flt, path_gjb);
                        }
                    } finally {
                        cleanup(path_nsb, path_flt, path_gjb);
                    }
                    logger.info("Updating GTFS-data - done: {} ms", (System.currentTimeMillis() - t1));
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
        Map<String, List<org.onebusaway.gtfs.model.StopTime>> tmpTripStops = new HashMap<>();
        Map<String, Set<String>> tmpTrainNumberTrips = new HashMap<>();
        Map<String, List<org.onebusaway.gtfs.model.calendar.ServiceDate>> tmpTripDates = new HashMap<>();
        Map<String, String> tmpParentStops = new HashMap<>();

        for (String path : paths) {
            readGTFS(path, tmpTripStops, tmpTrainNumberTrips, tmpTripDates, tmpParentStops);
        }

        // Swapping updated data
        tripStops = convertStops(tmpTripStops);
        trainNumberTrips = tmpTrainNumberTrips;
        tripDates = convertDates(tmpTripDates);
        parentStops = tmpParentStops;
        logger.info("Read and merged {} GTFS files in {} ms", paths.length, (System.currentTimeMillis()-start));
    }

    private static Map<String, List<ServiceDate>> convertDates(Map<String, List<org.onebusaway.gtfs.model.calendar.ServiceDate>> tripDates) {
        HashMap<String, List<ServiceDate>> result = new HashMap<>();
        for (Map.Entry<String, List<org.onebusaway.gtfs.model.calendar.ServiceDate>> entry : tripDates.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream().map(s -> new ServiceDate(s.getYear(), s.getMonth(), s.getDay())).collect(Collectors.toList()));
        }
        return result;
    }

    private static Map<String, List<StopTime>> convertStops(Map<String, List<org.onebusaway.gtfs.model.StopTime>> tripStops) {
        HashMap<String, List<StopTime>> result = new HashMap<>();
        for (Map.Entry<String, List<org.onebusaway.gtfs.model.StopTime>> entry : tripStops.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream().map(s -> new StopTime(s.getStop().getId().getId(), s.getStopSequence(), s.getArrivalTime(), s.getDepartureTime())).collect(Collectors.toList()));
        }
        return result;
    }

    private static void readGTFS(String path, Map<String, List<org.onebusaway.gtfs.model.StopTime>> tmpTripStops, Map<String, Set<String>> tmpTrainNumberTrips,
                                 Map<String, List<org.onebusaway.gtfs.model.calendar.ServiceDate>> tmpTripDates, Map<String, String> tmpParentStops) {
        try {
            GtfsReader reader = new GtfsReader();
            reader.setInputLocation(new File(path));
            reader.setDefaultAgencyId("ENT");
            GtfsRelationalDaoImpl dao = readDao(reader);

            for (Stop stop : dao.getAllStops()) {
                if (stop.getParentStation() != null) {
                    tmpParentStops.put(stop.getId().getId(), stop.getParentStation());
                }
            }

            Collection<org.onebusaway.gtfs.model.StopTime> allStopTimes = dao.getAllStopTimes();

            for (org.onebusaway.gtfs.model.StopTime stopTime : allStopTimes) {

                Trip trip = stopTime.getTrip();
                String serviceJourneyId = trip.getId().getId();
                String trainNr = serviceJourneyId.substring(serviceJourneyId.lastIndexOf("-") + 1);

                List<org.onebusaway.gtfs.model.StopTime> stops = tmpTripStops.getOrDefault(serviceJourneyId, new ArrayList<>());
                stops.add(stopTime);
                tmpTripStops.put(serviceJourneyId, stops);

                Set<String> trips = tmpTrainNumberTrips.getOrDefault(trainNr, new HashSet<>());
                trips.add(serviceJourneyId);

                tmpTrainNumberTrips.put(trainNr, trips);


                List<ServiceCalendarDate> calendarDatesForServiceId = dao.getCalendarDatesForServiceId(trip.getServiceId());

                tmpTripDates.put(serviceJourneyId,
                        calendarDatesForServiceId.stream()
                                .map(ServiceCalendarDate::getDate)
                                .collect(Collectors.toList())
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Could not read GTFS", e);
        }

    }

    private static GtfsRelationalDaoImpl readDao(GtfsReader reader) throws IOException {
        GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
        reader.setEntityStore(dao);
        reader.setDefaultAgencyId("ENT");
        reader.run();
        return dao;
    }


    private static String readUrl(String url) {

        InputStream httpIn = null;
        OutputStream fileOutput = null;
        OutputStream bufferedOut = null;
        try {
            long start = System.currentTimeMillis();
            File tmpFile = File.createTempFile("gtfs", ".zip");
            // check the http connection before we do anything to the fs
            httpIn = new BufferedInputStream(new URL(url).openStream());
            fileOutput = new FileOutputStream(tmpFile);
            bufferedOut = new BufferedOutputStream(fileOutput, 1024);
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
            e.printStackTrace();
        } finally {
            try {
                bufferedOut.close();
                fileOutput.close();
                httpIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}
