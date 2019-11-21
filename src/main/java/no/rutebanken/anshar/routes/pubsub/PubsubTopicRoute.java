package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PubsubTopicRoute extends RouteBuilder {

    @Value("${anshar.outbound.camel.route.topic.et.name}")
    private String etPubsubTopic;

    @Override
    public void configure() {

        from("direct:send.to.pubsub.topic.estimated_timetable")
                .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                .split().tokenizeXML("Siri").streaming()
                .to("direct:map.jaxb.to.protobuf")
                .to(etPubsubTopic)
        ;
    }
}

