package no.rutebanken.anshar.routes.mqtt;

import no.rutebanken.anshar.routes.Constants;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.format.DateTimeFormatter;

@Configuration
@Component
public class SiriVmMqttRoute extends RouteBuilder implements CamelContextAware {

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
                .to("mqtt:realtime?host=" + host + "&publishTopicName=" + topic + "&userName=" + username + "&password=" + password);
        }
    }


    public void pushToMqttRoute(String datasetId, VehicleActivityStructure activity) {
        if (!mqttEnabled) {
            return;
        }

        ProducerTemplate template = camelContext.createProducerTemplate();
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        String direction = monitoredVehicleJourney.getDirectionRef().getValue();
        String departureDay = monitoredVehicleJourney.getOriginAimedDepartureTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String departureTime = monitoredVehicleJourney.getOriginAimedDepartureTime().format(DateTimeFormatter.ofPattern("HHmm"));
        String lineRef = monitoredVehicleJourney.getLineRef().getValue();
        String stopIndex = monitoredVehicleJourney.getMonitoredCall().getVisitNumber().toString();
        String json = "{" +
                "    \"VP\": {" +
                "        \"desi\": \"E\"," +
                "        \"dir\": \"" + direction + "\"," +
                "        \"dl\": 0," +
                "        \"hdg\": 179," +
                "        \"jrn\": \"XXX\"," +
                "        \"lat\": " + monitoredVehicleJourney.getVehicleLocation().getLatitude().doubleValue() + "," +
                "        \"long\": " + monitoredVehicleJourney.getVehicleLocation().getLongitude().doubleValue() + "," +
                "        \"line\": \"" + lineRef + "\"," +
                "        \"oday\": \"" + departureDay + "\"," +
                "        \"oper\": \"" + datasetId + "\"," +
                "        \"source\": \"rutebanken\"," +
                "        \"spd\": 5.28," +
                "        \"start\": \"" + departureTime + "\"," +
                "        \"stop_index\": " + stopIndex + "," +
                "        \"tsi\": 1493710213," +
                "        \"tst\": \"2017-30-02T09:30:13.000Z\"," +
                "        \"veh\": \"2\"" +
                "    }" +
                "}";
        template.sendBody(Constants.MQTT_ROUTE_ID, json);
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
