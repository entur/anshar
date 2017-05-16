package no.rutebanken.anshar.routes.mqtt;

import javafx.util.Pair;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;
import uk.org.siri.siri20.VehicleActivityStructure.MonitoredVehicleJourney;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@Component
public class SiriVmMqttHandler {
    private Logger logger = LoggerFactory.getLogger(SiriVmMqttHandler.class);

    private static final String TOPIC_PREFIX = "/hfp/journey/";

    private static final String ZERO = "0";
    private static final String SLASH = "/";
    private static final String JOURNEY_DELIM = " -> ";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String ODAY_FORMAT = "hhmm";

    private static final String RUTEBANKEN = "rutebanken";

    @Value("${anshar.mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${anshar.mqtt.subscribe:false}")
    private boolean mqttSubscribe;

    @Value("${anshar.mqtt.host}")
    private String host;

    @Value("${anshar.mqtt.username}")
    private String username;

    @Value("${anshar.mqtt.password}")
    private String password;

    @Value("${anshar.mqtt.reconnectInterval.millis:30000}")
    private long reconnectInterval;

    private MqttClient mqttClient;

    private long lastConnectionAttempt;

    private final AtomicInteger publishCounter = new AtomicInteger(0);
    private Long publishedSizeCounter = new Long(0);
    private int connectionTimeout = 10;;

    @PostConstruct
    private void initialize() {
        try {
            String clientId = UUID.randomUUID().toString();
            mqttClient = new MqttClient(host, clientId, null);
            logger.info("Initializing MQTT-client with clientId {}", clientId);
        } catch (MqttException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @PreDestroy
    private void disconnect() {
        if (mqttClient.isConnected()) {
            try {
                logger.info("Disconnecting from MQTT on address {}", host);
                mqttClient.disconnectForcibly();
            } catch (MqttException e) {
                //Disconnect failed - ignore
            }
        }
    }

    public void publishMessage(String topic, String content) {

        if (!mqttClient.isConnected() &&
                (System.currentTimeMillis() - lastConnectionAttempt) > reconnectInterval) {
            connect();
        }

        try {
            if (mqttClient.isConnected()) {
                mqttClient.publish(topic, new MqttMessage(content.getBytes()));
                publishedSizeCounter += content.length();
                if (publishCounter.incrementAndGet() % 500 == 0) {
                    logger.info("This pod has published {} updates, with total size {} bytes, to MQTT since startup, last message:[{}]", publishCounter.get(), publishedSizeCounter.longValue(), content);
                }
            }
        } catch (MqttException e) {
            logger.info("Unable to publish to MQTT", e);
        }
    }

    private synchronized void connect() {
        if (mqttClient.isConnected()) {
            logger.info("Already connected to mqtt - ignoring connect-request");
            return;
        }

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setUserName(username);
        connOpts.setPassword(password.toCharArray());
        connOpts.setConnectionTimeout(connectionTimeout);
        connOpts.setMaxInflight(32000); //Half of the mqtt-specification
        connOpts.setCleanSession(false);
        try {
            lastConnectionAttempt = System.currentTimeMillis();
            logger.info("Connecting to MQTT on address {} using {} seconds timeout", host, connectionTimeout);
            mqttClient.connect(connOpts);
            logger.info("Connected to MQTT on address {} with user {}", host, username);
        } catch (MqttException e) {
            logger.warn("Failed to connect to MQTT ", e);
        }
    }

    public void pushToMqtt(String datasetId, VehicleActivityStructure activity) {
        if (!mqttEnabled) {
            return;
        }

        try {
            Pair<String, String> message = getMessage(datasetId, activity);
            publishMessage(message.getKey(), message.getValue());

        } catch (NullPointerException e) {
            logger.warn("Incomplete Siri data", e);
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

        String topic = getTopic(mode, vehicleId, route, direction, headSign, startTime, nextStop, lat, lng);
        String message = getMessage(monitoredVehicleJourney, vehicleId, timestamp, tsi, route, direction, headSign, startTime, lat, lng);

        return new Pair<>(topic, message);
    }

    /**
     * Formats topic to string
     * - hfp/journey/<mode>/<vehicleId>/<route>/<direction>/<headsign>/<start_time>/<next_stop>/<geohash>;
     */
    private String getTopic(String mode, String vehicleId, String route, String direction, String headSign,
                            String startTime, String nextStop, double lat, double lng) {
        return new StringBuilder(TOPIC_PREFIX)
                    .append(mode).append(SLASH)
                    .append(vehicleId).append(SLASH)
                    .append(route).append(SLASH)
                    .append(direction).append(SLASH)
                    .append(headSign).append(SLASH)
                    .append(startTime).append(SLASH)
                    .append(nextStop).append(SLASH)
                    .append(getGeoHash(lat, lng)).toString();
    }

    private String getMessage(MonitoredVehicleJourney monitoredVehicleJourney, String vehicleId, String timeStamp,
                              long tsi, String route, String direction, String headSign, String startTime, double lat,
                              double lng) {
        JSONObject vehiclePosition = new JSONObject();
        vehiclePosition.put(VehiclePosition.DESIGNATION, getDesignation(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.DIRECTION, direction);
        vehiclePosition.put(VehiclePosition.OPERATOR, getOperator(monitoredVehicleJourney));
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
        vehiclePosition.put(VehiclePosition.JOURNEY, getJourney(monitoredVehicleJourney, headSign));
        vehiclePosition.put(VehiclePosition.LINE, route);
        vehiclePosition.put(VehiclePosition.STARTTIME, startTime);
        vehiclePosition.put(VehiclePosition.STOP_INDEX, getStopIndex(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.SOURCE, RUTEBANKEN);

        return new JSONObject().put(VehiclePosition.ROOT, vehiclePosition).toString();
    }

    /*
     * MQTT helper methods
     */

    private String getMode(MonitoredVehicleJourney monitoredVehicleJourney) {
        if ("Sporvognsdrift".equals(getOperator(monitoredVehicleJourney))) {
            return "tram";
        }
        return "bus";
    }

    private String getVehicleId(String datasetId, MonitoredVehicleJourney monitoredVehicleJourney) {
        VehicleRef vehicleRef = monitoredVehicleJourney.getVehicleRef();
        if (vehicleRef != null && vehicleRef.getValue() != null) {
            return datasetId + vehicleRef.getValue();
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
            return directionRef.getValue();
        }
        return ZERO;
    }

    private String getHeadSign(MonitoredVehicleJourney monitoredVehicleJourney) {
        String departureName = VehiclePosition.UNKNOWN;
        List<NaturalLanguageStringStructure> destinationNames = monitoredVehicleJourney.getDestinationNames();
        if (destinationNames.size() > 0) {
            NaturalLanguageStringStructure destinationName = destinationNames.get(0);
            if (destinationName != null && destinationName.getValue() != null) {
                departureName = destinationName.getValue();
            }
        }
        return departureName;
    }

    private String getStartTime(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime();

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
        return 0.0;
    }

    private double getLongitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLongitude().doubleValue();
        }
        return 0.0;
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

    private String getOperator(MonitoredVehicleJourney monitoredVehicleJourney) {
        OperatorRefStructure operatorRef = monitoredVehicleJourney.getOperatorRef();
        if (operatorRef != null && operatorRef.getValue() != null) {
            return operatorRef.getValue();
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getTimestamp(VehicleActivityStructure monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getRecordedAtTime();
        if (locationRecordedAtTime != null) {
            return DateTimeFormatter.ISO_INSTANT.format(locationRecordedAtTime);
        }
        return VehiclePosition.UNKNOWN;
    }

    private long getTsi(VehicleActivityStructure monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getRecordedAtTime();
        if (locationRecordedAtTime != null) {
            return locationRecordedAtTime.toEpochSecond();
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
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime();

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

    private String getJourney(MonitoredVehicleJourney monitoredVehicleJourney, String headSign) {
        String origin = VehiclePosition.UNKNOWN;

        List<NaturalLanguagePlaceNameStructure> originNames = monitoredVehicleJourney.getOriginNames();
        if (originNames != null && originNames.size() > 0) {
            NaturalLanguagePlaceNameStructure originName = originNames.get(0);
            if (originName != null && originName.getValue() != null) {
                origin = originName.getValue();
            }
        }
        return origin.concat(JOURNEY_DELIM).concat(headSign);
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
}
