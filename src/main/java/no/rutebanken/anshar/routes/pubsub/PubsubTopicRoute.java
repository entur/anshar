package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PubsubTopicRoute extends RouteBuilder {

    @Value("${anshar.outbound.pubsub.topic.et.topicname}")
    private String etPubsubTopic;

    @Override
    public void configure() {

        final String SIRI_ET_TOPIC_ROUTE = "activemq:topic:anshar.outbound.estimated_timetable";
//        final String SIRI_ET_TOPIC_ROUTE = "entur-google-pubsub://" + etPubsubTopic;

        from("direct:send.to.pubsub.topic.estimated_timetable")
                .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                .split().tokenizeXML("Siri").streaming()
                .to("direct:map.jaxb.to.protobuf")
                .to(SIRI_ET_TOPIC_ROUTE)
        ;

    }
}

