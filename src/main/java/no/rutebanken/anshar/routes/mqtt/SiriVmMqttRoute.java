package no.rutebanken.anshar.routes.mqtt;

import javafx.util.Pair;
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

        BigDecimal lat = getLatitude(monitoredVehicleJourney);
        BigDecimal lng = getLongitude(monitoredVehicleJourney);

        JSONObject vp = new JSONObject();
        vp.put("desi", getDesignation(monitoredVehicleJourney));
        vp.put("dir",  getDirection(monitoredVehicleJourney));
        vp.put("oper", getOperator(monitoredVehicleJourney));
        vp.put("veh", vehicleId);
        vp.put("tst", getTimestamp(monitoredVehicleJourney));
        vp.put("tsi", getTsi(monitoredVehicleJourney));
        //vp.put("hdg", 9); UNKNOWN
        //vp.put("spd", 28); UNKNOWN
        if (lat != null && lng != null) {
            vp.put("lat", lat.doubleValue());
            vp.put("long", lng.doubleValue());
        }
        vp.put("dl", getDelay(monitoredVehicleJourney));
        //vp.put("odo": odometer); UNKNOWN
        vp.put("oday", getDepartureDay(monitoredVehicleJourney));
        vp.put("jrn", getJourney(monitoredVehicleJourney));
        vp.put("line", getLine(monitoredVehicleJourney));
        vp.put("start", getStartTime(monitoredVehicleJourney));
        vp.put("stop_index", getStopIndex(monitoredVehicleJourney));
        vp.put("source", "rutebanken");

        try {
            template.sendBody(Constants.MQTT_ROUTE_ID, vp.toString());
        } catch (Exception e) {
            //TODO logger.warn("Could not parse", e);
        }
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
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeException exception) {
                //TODO logger.warn("Could not format " + originAimedDepartureTime, e);
            }
        }
        return date;
    }

    private String getJourney(MonitoredVehicleJourney monitoredVehicleJourney) {
        String origin = UNKNOWN, departure = UNKNOWN;

        List<NaturalLanguagePlaceNameStructure> originNames = monitoredVehicleJourney.getOriginNames();
        if (originNames != null && originNames.size() > 0) {
            NaturalLanguagePlaceNameStructure originName = originNames.get(0);
            if (originName != null) {
                origin = originName.getValue();
            }
        }
        List<NaturalLanguageStringStructure> destinationNames = monitoredVehicleJourney.getDestinationNames();
        if (destinationNames != null && destinationNames.size() > 0) {
            NaturalLanguageStringStructure destinationName = destinationNames.get(0);
            if (destinationName != null) {
                departure = destinationName.getValue();
            }
        }
        return origin.concat(" -> ").concat(departure);
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
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern("hhmm"));
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
