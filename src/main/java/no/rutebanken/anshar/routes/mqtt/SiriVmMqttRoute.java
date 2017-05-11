package no.rutebanken.anshar.routes.mqtt;

import no.rutebanken.anshar.routes.Constants;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;
import uk.org.siri.siri20.VehicleActivityStructure.MonitoredVehicleJourney;

import javax.xml.datatype.Duration;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Configuration
@Component
public class SiriVmMqttRoute extends RouteBuilder implements CamelContextAware {

    private static final String UNKNOWN = "XXX";
    private static final String ZERO = "0";
    private static final String SLASH = "/";
    private static final String JOURNEY_DELIM = " -> ";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String ODAY_FORMAT = "hhmm";

    @Value("${anshar.mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${anshar.mqtt.host}")
    private String host;

    @Value("${anshar.mqtt.topic}")
    private String topic;

    @Value("${anshar.mqtt.username}")
    private String username;

    @Value("${anshar.mqtt.password}")
    private String password;

    @Autowired
    private CamelContext camelContext;

    @Override
    public void configure() throws Exception {

        if (mqttEnabled) {
            from("direct:mqtt-forwarder")
                .routeId(Constants.MQTT_ROUTE_ID)
                .to("mqtt:realtime?host=" + host + "&userName=" + username + "&password=" + password);
        }
    }


    public void pushToMqttRoute(String datasetId, VehicleActivityStructure activity) {
        if (!mqttEnabled) {
            return;
        }

        ProducerTemplate template = camelContext.createProducerTemplate();
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney == null) {
            return;
        }

        String vehicleId = getVehicleId(datasetId, monitoredVehicleJourney);
        if (vehicleId == null) {
            return;
        }
        String mode = getMode(monitoredVehicleJourney);
        String line = getLine(monitoredVehicleJourney);
        String direction = getDirection(monitoredVehicleJourney);
        String startTime = getStartTime(monitoredVehicleJourney);
        long stopIndex = getStopIndex(monitoredVehicleJourney);
        String departureName = getDepartureName(monitoredVehicleJourney);
        String operator = getOperator(monitoredVehicleJourney);

        BigDecimal lat = getLatitude(monitoredVehicleJourney);
        BigDecimal lng = getLongitude(monitoredVehicleJourney);
        String geoHash = "";

        JSONObject vp = new JSONObject();
        vp.put("desi", getDesignation(monitoredVehicleJourney));
        vp.put("dir", direction);
        vp.put("oper", operator);
        vp.put("veh", vehicleId);
        vp.put("tst", getTimestamp(monitoredVehicleJourney));
        vp.put("tsi", getTsi(monitoredVehicleJourney));
        //vp.put("hdg", 9); UNKNOWN
        //vp.put("spd", 28); UNKNOWN
        if (lat != null && lng != null) {
            vp.put("lat", lat.doubleValue());
            vp.put("long", lng.doubleValue());
            geoHash = getGeoHash(lat.doubleValue(), lng.doubleValue());
        }
        vp.put("dl", getDelay(monitoredVehicleJourney));
        //vp.put("odo": odometer); UNKNOWN
        vp.put("oday", getDepartureDay(monitoredVehicleJourney));
        vp.put("jrn", getJourney(monitoredVehicleJourney, departureName));
        vp.put("line", line);
        vp.put("start", startTime);
        vp.put("stop_index", stopIndex);
        vp.put("source", "rutebanken");

        StringBuilder topic = new StringBuilder("/hfp/journey/");
        topic.append(mode).append(SLASH)
                .append(vehicleId).append(SLASH)
                .append(line).append(SLASH)
                .append(direction).append(SLASH)
                .append(departureName).append(SLASH)
                .append(startTime).append(SLASH)
                .append(stopIndex).append(SLASH)
                .append(geoHash);

        try {
            template.sendBody(Constants.MQTT_ROUTE_ID, vp.toString());
        } catch (Exception e) {
            //TODO logger.warn("Could not parse", e);
        }
    }

    private String getMode(MonitoredVehicleJourney monitoredVehicleJourney) {
        if (getOperator(monitoredVehicleJourney).equals("Sporvognsdrift")) {
            return "tram";
        }
        return "bus";
    }

    private String digit(double x, int i) {
        return "" + Math.floor((int)(x * Math.pow(10, i))) % 10;
    }

    private String digits(double latitude, double longitude) {
        int j;
        StringBuilder results = new StringBuilder(SLASH);
        for (int i = j = 1; j <= 3; i = ++j) {
            results.append(digit(latitude, i)).append(digit(longitude, i)).append(SLASH);
        }
        return results.toString();
    };

    private String getGeoHash(double latitude, double longitude) {
        StringBuilder geohash = new StringBuilder();
        geohash.append(Math.floor(latitude));
        geohash.append(";");
        geohash.append(Math.floor(longitude));
        geohash.append(digits(latitude, longitude));
        return geohash.toString();
    }

    private String getDesignation(MonitoredVehicleJourney monitoredVehicleJourney) {
        List<NaturalLanguageStringStructure> publishedLineNames = monitoredVehicleJourney.getPublishedLineNames();
        if (publishedLineNames != null && publishedLineNames.size() > 0) {
            NaturalLanguageStringStructure publishedLine = publishedLineNames.get(0);
            if (publishedLine != null) {
                return publishedLine.getValue();
            }
        }
        return UNKNOWN;
    }

    private String getDirection(MonitoredVehicleJourney monitoredVehicleJourney) {
        DirectionRefStructure directionRef = monitoredVehicleJourney.getDirectionRef();
        if (directionRef != null) {
            return directionRef.getValue();
        }
        return ZERO;
    }

    private String getOperator(MonitoredVehicleJourney monitoredVehicleJourney) {
        OperatorRefStructure operatorRef = monitoredVehicleJourney.getOperatorRef();
        if (operatorRef != null) {
            return operatorRef.getValue();
        }
        return UNKNOWN;
    }

    private String getVehicleId(String datasetId, MonitoredVehicleJourney monitoredVehicleJourney) {
        VehicleRef vehicleRef = monitoredVehicleJourney.getVehicleRef();
        if (vehicleRef != null) {
            return datasetId + vehicleRef.getValue();
        }
        return null;
    }

    private String getTimestamp(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getLocationRecordedAtTime();
        if (locationRecordedAtTime != null) {
            return locationRecordedAtTime.toString();
        }
        return UNKNOWN;
    }

    private long getTsi(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getLocationRecordedAtTime();
        if (locationRecordedAtTime != null) {
            return locationRecordedAtTime.toEpochSecond();
        }
        return 0;
    }

    private BigDecimal getLatitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLatitude();
        }
        return null;
    }

    private BigDecimal getLongitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLongitude();
        }
        return null;
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

        String date = UNKNOWN;
        if (originAimedDepartureTime != null) {
            try {
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
            } catch (DateTimeException exception) {
                //TODO logger.warn("Could not format " + originAimedDepartureTime, e);
            }
        }
        return date;
    }

    private String getDepartureName(MonitoredVehicleJourney monitoredVehicleJourney) {
        String departureName = UNKNOWN;
        List<NaturalLanguageStringStructure> destinationNames = monitoredVehicleJourney.getDestinationNames();
        if (destinationNames != null && destinationNames.size() > 0) {
            NaturalLanguageStringStructure destinationName = destinationNames.get(0);
            if (destinationName != null) {
                departureName = destinationName.getValue();
            }
        }
        return departureName;
    }

    private String getJourney(MonitoredVehicleJourney monitoredVehicleJourney, String departure) {
        String origin = UNKNOWN;

        List<NaturalLanguagePlaceNameStructure> originNames = monitoredVehicleJourney.getOriginNames();
        if (originNames != null && originNames.size() > 0) {
            NaturalLanguagePlaceNameStructure originName = originNames.get(0);
            if (originName != null) {
                origin = originName.getValue();
            }
        }
        return origin.concat(JOURNEY_DELIM).concat(departure);
    }

    private String getLine(MonitoredVehicleJourney monitoredVehicleJourney) {
        LineRef lineRef = monitoredVehicleJourney.getLineRef();
        if (lineRef != null) {
            return lineRef.getValue();
        }
        return UNKNOWN;
    }

    private String getStartTime(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime();

        String date = UNKNOWN;
        if (originAimedDepartureTime != null) {
            try {
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern(ODAY_FORMAT));
            } catch (DateTimeException exception) {
                //TODO logger.warn("Could not format " + originAimedDepartureTime, e);
            }
        }
        return date;
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

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
