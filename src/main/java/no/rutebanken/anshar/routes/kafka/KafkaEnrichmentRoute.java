package no.rutebanken.anshar.routes.kafka;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaEnrichmentRoute extends RouteBuilder {

    @Autowired
    private KafkaPublisher kafkaPublisher;

    @Autowired
    private KafkaEnrichmentConsumer kafkaConsumer;

    @Value("${anshar.kafka.siri.enrich.et.enabled}")
    private boolean kafkaEnrichEtEnabled;

    @Value("${anshar.kafka.siri.enrich.et.name:}")
    private String kafkaEnrichEtTopic;

    @Override
    public void configure() throws Exception {

        if (kafkaEnrichEtEnabled) {
            from("direct:anshar.enrich.siri.et")
                    .to("xslt-saxon:xsl/split.xsl")
                    .log("Added to kafka-enrichment")
                    .setHeader("topic", simple(kafkaEnrichEtTopic))
                    .bean(kafkaPublisher, "publishToKafka(${header.topic}, ${body}, ${headers})")
                    .routeId("anshar.enrich.siri.et.kafka")
            ;
        } else {
            // Immediately redirect to continue internal processing
            from("direct:anshar.enrich.siri.et")
                .to("direct:enqueue.message")
                .routeId("anshar.enrich.siri.et.kafka.redirect")
            ;
        }
    }
}
