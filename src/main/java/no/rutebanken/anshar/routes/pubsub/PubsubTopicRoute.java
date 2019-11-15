package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.anshar.routes.websocket.SiriWebsocketRoute.SIRI_ET_TOPIC_ROUTE;

@Service
public class PubsubTopicRoute extends RouteBuilder {

    @Value("${anshar.outbound.pubsub.topic.et.topicname}")
    private String etPubsubTopic;

    @Override
    public void configure() {

        String topicRoute = SIRI_ET_TOPIC_ROUTE;
//       String topicRoute = "entur-google-pubsub://" + etPubsubTopic;

        from("direct:send.to.pubsub.topic.estimated_timetable")
                .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                .split().tokenizeXML("Siri").streaming()
                .to("direct:map.jaxb.to.protobuf")
                .to(topicRoute)
        ;

    }
}

