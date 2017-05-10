package no.rutebanken.anshar.routes.mqtt;

import no.rutebanken.anshar.routes.Constants;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SiriVmMqttRoute extends RouteBuilder {

    @Value("${anshar.mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${anshar.mqtt.host")
    private String host;

    @Value("${anshar.mqtt.topic")
    private String topic;

    @Value("${anshar.mqtt.username}")
    private String username;

    @Value("${anshar.mqtt.password}")
    private String password;

    @Override
    public void configure() throws Exception {
        from("direct:mqtt-forwarder")
                .routeId(Constants.MQTT_ROUTE_ID)
                .choice().when(p -> mqttEnabled)
                    .to("mqtt:realtime?host=" + host + "&publishTopicName=" + topic + "&username=" + username + "&password=" + password)
                .end();
    }
}
