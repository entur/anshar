package no.rutebanken.anshar.routes.mqtt;


import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.paho.PahoConstants;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MqttProducerRoute extends RouteBuilder {

    private static final String clientId = UUID.randomUUID().toString();

    @Value("${anshar.mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${anshar.mqtt.host}")
    private String host;

    @Value("${anshar.mqtt.username}")
    private String username;

    @Value("${anshar.mqtt.password}")
    private String password;

    @Bean
    MqttConnectOptions connectOptions() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setServerURIs(new String[] {host});
        connectOptions.setUserName(username);
        connectOptions.setPassword(password.toCharArray());
        connectOptions.setMaxInflight(1000);
        connectOptions.setAutomaticReconnect(true);
        return connectOptions;
    }

    @Override
    public void configure() {

        if (mqttEnabled) {
            from("direct:send.to.mqtt")
                    .setHeader(PahoConstants.CAMEL_PAHO_OVERRIDE_TOPIC, simple("${header.topic}"))
                    .to("paho:default/topic?qos=1&clientId=" + clientId);
        }
    }
}
