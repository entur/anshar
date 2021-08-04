package no.rutebanken.anshar.routes.mqtt;


import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.component.paho.PahoConstants;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    private AtomicInteger counter = new AtomicInteger();
    private AtomicInteger queueLength = new AtomicInteger();

    @Override
    public void configure() {

        ThreadPoolProfile mqttThreadPool = new ThreadPoolProfileBuilder("mqtt-tp-profile")
                .maxPoolSize(1000)
                .maxQueueSize(2000)
                .poolSize(50)
                .rejectedPolicy(ThreadPoolRejectedPolicy.DiscardOldest)
                .build();

        getContext().getExecutorServiceManager().registerThreadPoolProfile(mqttThreadPool);



        from("direct:send.to.mqtt")
                .routeId("send.to.mqtt")
                .setHeader(PahoConstants.CAMEL_PAHO_OVERRIDE_TOPIC, simple("${header.topic}"))
                .wireTap("direct:post.to.paho.client").executorServiceRef("mqtt-tp-profile");

        if (mqttEnabled) {
            from("direct:post.to.paho.client")
                .routeId("post.to.paho.client")
                .to("paho:default/topic?qos=1&clientId=" + clientId + "&brokerUrl=" + host + "&userName=" + username + "&password=" + password)
                .to("direct:log.mqtt.traffic");
        } else {
            from("direct:post.to.paho.client")
                .routeId("disabled.post.to.paho.client")
                .log(LoggingLevel.DEBUG, "Skip sending to MQTT");
        }

        from("direct:log.mqtt.traffic")
                .routeId("log.mqtt")
                .process(p -> {
                    if (counter.incrementAndGet() % 1000 == 0) {
                        p.getOut().setHeader("counter", counter.get());
                        p.getOut().setBody(p.getIn().getBody());
                    }
                })
                .choice()
                .when(header("counter").isNotNull())
                .log("MQTT: Published ${header.counter} updates, last message: ${body}")
                .endChoice()
                .end();

    }

}
