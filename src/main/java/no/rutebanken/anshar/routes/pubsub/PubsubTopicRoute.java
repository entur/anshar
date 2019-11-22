package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PubsubTopicRoute extends RouteBuilder {

    @Value("${anshar.outbound.camel.route.topic.et.name}")
    private String etPubsubTopic;

    private AtomicInteger counter = new AtomicInteger();

    @Override
    public void configure() {

        from("direct:send.to.pubsub.topic.estimated_timetable")
                .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                .split().tokenizeXML("Siri").streaming()
                .to("direct:map.jaxb.to.protobuf")
                .wireTap("direct:log.pubsub.traffic")
                .to(etPubsubTopic)
        ;

        from("direct:log.pubsub.traffic")
                .routeId("log.pubsub")
                .process(p -> {
                    if (counter.incrementAndGet() % 1000 == 0) {
                        p.getOut().setHeader("counter", counter.get());
                        p.getOut().setBody(p.getIn().getBody());
                    }
                })
                .choice()
                .when(header("counter").isNotNull())
                .log("Pubsub: Published ${header.counter} updates")
                .endChoice()
                .end();
    }
}

