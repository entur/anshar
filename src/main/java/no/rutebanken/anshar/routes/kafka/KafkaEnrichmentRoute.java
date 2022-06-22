package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.config.AnsharConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT;

@Component
public class KafkaEnrichmentRoute extends KafkaConfig {

    @Autowired
    private AnsharConfiguration config;

    @Value("${anshar.kafka.siri.enrich.et.processed.name:}")
    private String kafkaEnrichEtProcessedTopic;

    @Value("${anshar.kafka.siri.enrich.et.enabled:false}")
    private boolean kafkaEnrichEtEnabled;

    @Value("${anshar.kafka.siri.enrich.et.name:}")
    private String kafkaEnrichEtTopic;

    @Override
    public void configure() throws Exception {

        if (kafkaEnrichEtEnabled && config.processAdmin()) {

            String kafkaProducerConfig = "kafka:" + createProducerConfig(kafkaEnrichEtTopic);

            String kafkaConsumerConfig = "kafka:" + createConsumerConfig(kafkaEnrichEtProcessedTopic);

            log.info("Kafka enrichment: producer: {}, consumer: {}", kafkaEnrichEtTopic, kafkaEnrichEtProcessedTopic);

            from("direct:anshar.enrich.siri.et")
                    .log("Adding to kafka-enrichment topic")
                    .to("log:kafka-producer:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .setHeader("topic", simple(kafkaEnrichEtTopic))
                    .removeHeader(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT)
                    .to("xslt-saxon:xsl/split.xsl")
                    .split().tokenizeXML("Siri").streaming()
                    .to(kafkaProducerConfig)
                    .routeId("anshar.enrich.siri.et.kafka.producer")
            ;

            from(kafkaConsumerConfig)
                    .log("Read from kafka-enrichment topic")
                    .process(p -> {
                        Map<String, Object> headers = p.getIn().getHeaders();
                        for (String header : headers.keySet()) {
                            Object value = headers.get(header);
                            if (value instanceof byte[]) {
                                p.getMessage().setHeader(header, new String((byte[]) value));
                            } else {
                                p.getMessage().setHeader(header, value);
                            }
                        }
                    })
                    .to("log:kafka-consume:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("direct:send.to.queue")
                    .routeId("anshar.enrich.siri.et.kafka.consumer")
            ;

        } else {
            // Immediately redirect to continue internal processing
            from("direct:anshar.enrich.siri.et")
                .log("Redirecting internally")
                .removeHeader(INTERNAL_PUBLISH_TO_KAFKA_FOR_APC_ENRICHMENT)
                .to("direct:send.to.queue")
                .routeId("anshar.enrich.siri.et.kafka.redirect")
            ;
        }
    }
}
