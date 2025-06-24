package no.rutebanken.anshar.routes.siri.processor.routedata;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.LocationStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.VehicleModesEnumeration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.locations;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.modes;

public class StopsUtil {

    private static final Logger logger = LoggerFactory.getLogger(StopsUtil.class);

    private static final LoadingCache<Pair<String, String>, Double> distanceCache = CacheBuilder.newBuilder()
        .expireAfterWrite(120, TimeUnit.MINUTES)
        .build(
            new CacheLoader<Pair<String, String>, Double>() {
                public Double load(Pair<String, String> fromAndTo) {

                    double distance = calculateOrthodromicDistance(fromAndTo);
                    if (distance < 0) {
                        // If distance is not found, retry once
                        distance = calculateOrthodromicDistance(fromAndTo);
                    }
                    return distance;

                }

                private static double calculateOrthodromicDistance(Pair<String, String> fromAndTo) {
                    double distance = -1;

                    final LocationStructure from = locations.get(fromAndTo.getLeft());
                    final LocationStructure to = locations.get(fromAndTo.getRight());

                    if (from != null && to!= null) {
                        Coordinate fromCoord = new Coordinate(from.getLongitude().doubleValue(), from.getLatitude().doubleValue());
                        Coordinate toCoord = new Coordinate(to.getLongitude().doubleValue(), to.getLatitude().doubleValue());
                        try {
                            distance = JTS.orthodromicDistance(
                                fromCoord,
                                toCoord,
                                DefaultGeographicCRS.WGS84);
                        }
                        catch (TransformException e) {
                            logger.warn("Could not calculate distance from {} to {}", fromCoord, toCoord, e);
                        }
                    }
                    return distance;
                }
            })
        ;

    public static boolean doesVehicleModeMatchStopMode(List<VehicleModesEnumeration> reportedModes, String stopRef) {
        final AllVehicleModesOfTransportEnumeration stopMode = modes.get(stopRef);
        if (stopMode != null && reportedModes != null && reportedModes.size() == 1) {
            final VehicleModesEnumeration mode = reportedModes.get(0);
            switch (mode) {
                case AIR:
                    return stopMode == AllVehicleModesOfTransportEnumeration.AIR;
                case BUS, COACH, TAXI:
                    return (stopMode == AllVehicleModesOfTransportEnumeration.BUS |
                            stopMode == AllVehicleModesOfTransportEnumeration.COACH |
                            stopMode == AllVehicleModesOfTransportEnumeration.TROLLEY_BUS
                    );
                case RAIL:
                    return stopMode == AllVehicleModesOfTransportEnumeration.RAIL;
                case TRAM, METRO:
                    return (stopMode == AllVehicleModesOfTransportEnumeration.TRAM |
                            stopMode == AllVehicleModesOfTransportEnumeration.METRO);
                case FERRY:
                    return (stopMode == AllVehicleModesOfTransportEnumeration.FERRY |
                            stopMode == AllVehicleModesOfTransportEnumeration.WATER);
            }
        }
        // No mode set - accept by default
        return true;
    }

    public static int calculateSpeedKph(String fromRef, String toRef, ZonedDateTime departureTime, ZonedDateTime arrivalTime) {
        return calculateSpeedKph(getDistance(fromRef, toRef), departureTime, arrivalTime);
    }

    //public for testing-purposes
    public static double getDistance(String fromRef, String toRef) {
        try {
            if (locations.isEmpty()) {
                logger.warn("No locations available for distance calculation. Returning 1000.");
                return 1000D; // Default distance if no locations are available
            }
            return distanceCache.get(Pair.of(fromRef, toRef));
        }
        catch (ExecutionException e) {
            return 0D;
        }
    }

    public static int calculateSpeedKph(double distanceInMeters, ZonedDateTime departureTime, ZonedDateTime arrivalTime) {
        final long seconds = getSeconds(departureTime, arrivalTime);
        return calculateSpeedKph(distanceInMeters, seconds);
    }

    public static int calculateSpeedKph(double distanceInMeters,long seconds) {
        if (seconds <= 0) {
            return Integer.MAX_VALUE;
        }
        double metersPerSecond = distanceInMeters/seconds;
        double kilometersPerHour = metersPerSecond * 3.6;
        return (int)kilometersPerHour;
    }

    public static long getSeconds(ZonedDateTime departureTime, ZonedDateTime arrivalTime) {
        return arrivalTime.toEpochSecond() - departureTime.toEpochSecond();
    }

}
