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

package no.rutebanken.anshar.routes.mqtt;

import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.DirectionRefStructure;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.MonitoredCallStructure;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.OnwardCallsStructure;
import uk.org.siri.siri20.OperatorRefStructure;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleActivityStructure.MonitoredVehicleJourney;
import uk.org.siri.siri20.VehicleModesEnumeration;
import uk.org.siri.siri20.VehicleRef;

import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;

@Configuration
@Component
public class SiriVmMqttHandler {
    private final Logger logger = LoggerFactory.getLogger(SiriVmMqttHandler.class);

    private static final String TOPIC_PREFIX = "/hfp/journey/";

    private static final String ZERO = "0";
    private static final String SLASH = "/";
    private static final String JOURNEY_DELIM = " -> ";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String ODAY_FORMAT = "hhmm";

    private static final String ENTUR = "entur";
    private static final String DIR_GO = "1";
    private static final String DIR_BACK = "2";
    private static final String GO = "go";
    private static final String BACK = "back";

    @Value("${anshar.mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${anshar.mqtt.destination.id.fallback:false}")
    private boolean destinationIdFallback;

    @Produce(uri = "direct:send.to.mqtt")
    ProducerTemplate mqttProducer;

    public void pushToMqttAsync(String datasetId, VehicleActivityStructure activity) {
        if (mqttEnabled) {
            Executors.newSingleThreadExecutor().submit(() -> pushToMqtt(datasetId, activity));
        }
    }

    private void pushToMqtt(String datasetId, VehicleActivityStructure activity) {
        if (!mqttEnabled) {
            return;
        }

        // If monitored == false, ignore update
        if (activity != null && activity.getMonitoredVehicleJourney() != null &&
                activity.getMonitoredVehicleJourney().isMonitored() != null &&
                !activity.getMonitoredVehicleJourney().isMonitored()) {
            return;
        }

        try {
            Pair<String, String> message = getMessage(datasetId, activity);

            mqttProducer.sendBodyAndHeader(message.getValue(), "topic", message.getKey());

        } catch (NullPointerException e) {
            logger.debug("Incomplete Siri data", e);
        } catch (Exception e) {
            logger.warn("Could not parse", e);
        }
    }

    public Pair<String, String> getMessage(String datasetId, VehicleActivityStructure activity) {
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney == null) {
            throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney is null");
        }

        String vehicleId = getVehicleId(datasetId, monitoredVehicleJourney);
        if (vehicleId == null) {
            throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney.VehicleRef is null");
        }

        String tripId = getServiceJourney(monitoredVehicleJourney);
        String mode = getMode(monitoredVehicleJourney);
        String route = getRoute(monitoredVehicleJourney);
        String direction = getDirection(monitoredVehicleJourney);
        String headSign = getHeadSign(monitoredVehicleJourney);
        String startTime = getStartTime(monitoredVehicleJourney);
        String nextStop = getNextStop(monitoredVehicleJourney);
        double lat = getLatitude(monitoredVehicleJourney);
        double lng = getLongitude(monitoredVehicleJourney);
        String timestamp = getTimestamp(activity);
        long tsi = getTsi(activity);

        String topic = getTopic(mode, vehicleId, route, tripId, direction, headSign, startTime, nextStop, lat, lng);
        String message = null;
        try {
            message = getMessage(monitoredVehicleJourney, vehicleId, timestamp, tsi, route, tripId, direction, headSign, startTime, lat, lng, mode);
        } catch (JSONException e) {
           logger.info("Caught exception when generating MQTT-messsage - will be ignored", e);
        }
        
        return Pair.of(topic, message);
    }

    /**
     * Formats topic to string
     * - hfp/journey/<mode>/<vehicleId>/<route>/<trip_id>/<direction>/<headsign>/<start_time>/<next_stop>/<geohash>;
     */
    private String getTopic(String mode, String vehicleId, String route, String tripId, String direction, String headSign,
                            String startTime, String nextStop, double lat, double lng) {
        return new StringBuilder(TOPIC_PREFIX)
                    .append(mode).append(SLASH)
                    .append(vehicleId).append(SLASH)
                    .append(route).append(SLASH)
                    .append(tripId).append(SLASH)
                    .append(direction).append(SLASH)
                    .append(headSign).append(SLASH)
                    .append(startTime).append(SLASH)
                    .append(nextStop).append(SLASH)
                    .append(getGeoHash(lat, lng)).toString();
    }

    private String getMessage(MonitoredVehicleJourney monitoredVehicleJourney, String vehicleId, String timeStamp,
                              long tsi, String route, String tripId, String direction, String headSign, String startTime, double lat,
                              double lng, String mode) throws JSONException {
        JSONObject vehiclePosition = new JSONObject();
        vehiclePosition.put(VehiclePosition.DESIGNATION, getDesignation(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.DIRECTION, direction);
        vehiclePosition.put(VehiclePosition.OPERATOR, getDataSource(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.VEHICLE_ID, vehicleId);
        vehiclePosition.put(VehiclePosition.TIMESTAMP, timeStamp);
        vehiclePosition.put(VehiclePosition.TSI, tsi);
        //vehiclePosition.put(VehiclePosition.HEADING, 9);
        //vehiclePosition.put(VehiclePosition.SPEED, 28);
        vehiclePosition.put(VehiclePosition.LATITUDE, lat);
        vehiclePosition.put(VehiclePosition.LONGITUDE, lng);
        vehiclePosition.put(VehiclePosition.DELAY, getDelay(monitoredVehicleJourney));
        //vehiclePosition.put(VehiclePosition.ODOMETER: odometer);
        vehiclePosition.put(VehiclePosition.ODAY, getDepartureDay(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.JOURNEY, getJourney(headSign));
        vehiclePosition.put(VehiclePosition.LINE, route);
        vehiclePosition.put(VehiclePosition.TRIP_ID, tripId);
        vehiclePosition.put(VehiclePosition.STARTTIME, startTime);
        vehiclePosition.put(VehiclePosition.STOP_INDEX, getStopIndex(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.SOURCE, ENTUR);
        vehiclePosition.put(VehiclePosition.MODE, mode);

        return new JSONObject().put(VehiclePosition.ROOT, vehiclePosition).toString();
    }

    /*
     * MQTT helper methods
     */

    private String getServiceJourney(MonitoredVehicleJourney monitoredVehicleJourney) {
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = monitoredVehicleJourney.getFramedVehicleJourneyRef();
        if (framedVehicleJourneyRef != null && framedVehicleJourneyRef.getDatedVehicleJourneyRef() != null) {
            return OutboundIdAdapter.getMappedId(framedVehicleJourneyRef.getDatedVehicleJourneyRef());
        }
        if (monitoredVehicleJourney.getCourseOfJourneyRef() != null) {
            // Backup-solution for Kolumbus/AtB
            return OutboundIdAdapter.getMappedId(monitoredVehicleJourney.getCourseOfJourneyRef().getValue());
        }
        return VehiclePosition.UNKNOWN;
    }


    private String getMode(MonitoredVehicleJourney monitoredVehicleJourney) {
        String vehicleMode = getVehicleMode(monitoredVehicleJourney);
        if (vehicleMode != null) {
            switch (vehicleMode) {
                case "ferry":
                    return "water";
                default:
                    return vehicleMode;
            }
        }

        if ("Sporvognsdrift".equals(getOperator(monitoredVehicleJourney))) {
            return "tram";
        }
        if ("Tide_sjø_AS".equals(getOperator(monitoredVehicleJourney))) {
            return "water";
        }
        return "bus";
    }

    private String getVehicleId(String datasetId, MonitoredVehicleJourney monitoredVehicleJourney) {
        VehicleRef vehicleRef = monitoredVehicleJourney.getVehicleRef();
        if (vehicleRef != null && vehicleRef.getValue() != null) {
            return datasetId + ":" + vehicleRef.getValue();
        }
        return null;
    }

    private String getRoute(MonitoredVehicleJourney monitoredVehicleJourney) {
        LineRef lineRef = monitoredVehicleJourney.getLineRef();
        if (lineRef != null && lineRef.getValue() != null) {
            return OutboundIdAdapter.getMappedId(lineRef.getValue());
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getDirection(MonitoredVehicleJourney monitoredVehicleJourney) {
        DirectionRefStructure directionRef = monitoredVehicleJourney.getDirectionRef();
        if (directionRef != null && directionRef.getValue() != null) {
            String value = directionRef.getValue().toLowerCase();
            if (value.equals(GO)) {
                return DIR_GO;
            } else if (value.equals(BACK)){
                return DIR_BACK;
            }
            return value;
        }
        return DIR_GO;
    }

    private String getHeadSign(MonitoredVehicleJourney monitoredVehicleJourney) {
        String headsign = VehiclePosition.UNKNOWN;
        List<NaturalLanguageStringStructure> destinationNames = monitoredVehicleJourney.getDestinationNames();
        if (destinationNames.size() > 0) {
            NaturalLanguageStringStructure destinationName = destinationNames.get(0);
            if (destinationName != null &&
                    destinationName.getValue() != null &&
                    !destinationName.getValue().isEmpty()) {
                headsign = destinationName.getValue();
            }
        }
        if (destinationIdFallback &&
                (VehiclePosition.UNKNOWN.equals(headsign))) {
            // Destination name does not exist, and fallback to id is configured
            if (monitoredVehicleJourney.getDestinationRef() != null) {
                headsign = OutboundIdAdapter.getMappedId(monitoredVehicleJourney.getDestinationRef().getValue());
            }
        }
        return headsign;
    }

    private String getStartTime(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime() != null ?
                monitoredVehicleJourney.getOriginAimedDepartureTime(): ZonedDateTime.now();

        String date = VehiclePosition.UNKNOWN;
        if (originAimedDepartureTime != null) {
            try {
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern(ODAY_FORMAT));
            } catch (DateTimeException exception) {
                logger.warn("Could not format " + originAimedDepartureTime + " to " + ODAY_FORMAT, exception);
            }
        }
        return date;
    }

    private String getNextStop(MonitoredVehicleJourney monitoredVehicleJourney) {
        OnwardCallsStructure onwardCalls = monitoredVehicleJourney.getOnwardCalls();
        if (onwardCalls != null && onwardCalls.getOnwardCalls().size() > 0) {
            StopPointRef stopPointRef = onwardCalls.getOnwardCalls().get(0).getStopPointRef();
            if (stopPointRef != null && stopPointRef.getValue() != null) {
                return OutboundIdAdapter.getMappedId(stopPointRef.getValue());
            }
        }
        return VehiclePosition.UNKNOWN;
    }

    private double getLatitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLatitude().doubleValue();
        }
        throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney.VehicleLocation.Latitude not set");
    }

    private double getLongitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLongitude().doubleValue();
        }
        throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney.VehicleLocation.Longitude not set");
    }

    private String getGeoHash(double latitude, double longitude) {
        StringBuilder geohash = new StringBuilder();
        geohash.append((int)latitude);
        geohash.append(";");
        geohash.append((int)longitude);
        geohash.append(digits(latitude, longitude));
        return geohash.toString();
    }

    /*
     * MQTT Message helper methods
     */

    private String getDesignation(MonitoredVehicleJourney monitoredVehicleJourney) {
        List<NaturalLanguageStringStructure> publishedLineNames = monitoredVehicleJourney.getPublishedLineNames();
        if (publishedLineNames != null && publishedLineNames.size() > 0) {
            NaturalLanguageStringStructure publishedLine = publishedLineNames.get(0);
            if (publishedLine != null && publishedLine.getValue() != null) {
                return publishedLine.getValue();
            }
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getVehicleMode(MonitoredVehicleJourney monitoredVehicleJourney) {
        List<VehicleModesEnumeration> vehicleModes = monitoredVehicleJourney.getVehicleModes();
        if (vehicleModes != null && !vehicleModes.isEmpty()) {
            return vehicleModes.get(0).value();
        }
        return null;
    }

    private String getOperator(MonitoredVehicleJourney monitoredVehicleJourney) {
        OperatorRefStructure operatorRef = monitoredVehicleJourney.getOperatorRef();
        if (operatorRef != null && operatorRef.getValue() != null) {
            return operatorRef.getValue();
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getDataSource(MonitoredVehicleJourney monitoredVehicleJourney) {
        return monitoredVehicleJourney.getDataSource();
    }

    private String getTimestamp(VehicleActivityStructure monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getRecordedAtTime();
        if (locationRecordedAtTime != null) {

            long epochSecond = locationRecordedAtTime.toEpochSecond();
            if (epochSecond < 0) {
                return DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.now());
            }
            return DateTimeFormatter.ISO_INSTANT.format(locationRecordedAtTime);
        }
        return VehiclePosition.UNKNOWN;
    }

    private long getTsi(VehicleActivityStructure monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getRecordedAtTime();
        if (locationRecordedAtTime != null) {

            long epochSecond = locationRecordedAtTime.toEpochSecond();
            if (epochSecond < 0) {
                return ZonedDateTime.now().toEpochSecond();
            }
            return epochSecond;
        }
        return 0;
    }

    private int getDelay(MonitoredVehicleJourney monitoredVehicleJourney) {
        Duration delay = monitoredVehicleJourney.getDelay();
        if (delay != null) {
            return delay.getSeconds();
        }
        return 0;
    }

    private String getDepartureDay(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime() != null ?
                monitoredVehicleJourney.getOriginAimedDepartureTime(): ZonedDateTime.now();

        String date = VehiclePosition.UNKNOWN;
        if (originAimedDepartureTime != null) {
            try {
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
            } catch (DateTimeException exception) {
                logger.warn("Could not format " + originAimedDepartureTime + " to " + DATE_FORMAT, exception);
            }
        }
        return date;
    }

    private String getJourney(String headSign) {
        if (headSign.isEmpty() | headSign.equals(VehiclePosition.UNKNOWN)) {
            throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney.DestinationName not set");
        }

        return headSign;
    }

    private long getStopIndex(MonitoredVehicleJourney monitoredVehicleJourney) {
        MonitoredCallStructure monitoredCall = monitoredVehicleJourney.getMonitoredCall();
        if (monitoredCall != null) {
            BigInteger visitNumber = monitoredCall.getVisitNumber();
            if (visitNumber != null) {
                return visitNumber.longValue();
            }
        }
        return 0;
    }

    /*
     * GeoHash Helper methods
     */

    private String digit(double x, int i) {
        return "" + (int) (Math.floor(x * Math.pow(10, i)) % 10);
    }

    private String digits(double latitude, double longitude) {
        int j;
        StringBuilder results = new StringBuilder(SLASH);
        for (int i = j = 1; j <= 3; i = ++j) {
            results.append(digit(latitude, i)).append(digit(longitude, i)).append(SLASH);
        }
        return results.toString();
    }

    private static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
