package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class KafkaPublisherRoute extends KafkaConfig {

    private static final int LOG_INTERVAL_ET = 1000;
    private static final int LOG_INTERVAL_VM = 10000;
    private static final int LOG_INTERVAL_SX = 100;
    @Value("${anshar.kafka.topic.et.name:}")
    private String kafkaEtTopic;

    @Value("${anshar.kafka.topic.vm.name:}")
    private String kafkaVmTopic;

    @Value("${anshar.kafka.topic.sx.name:}")
    private String kafkaSxTopic;

    @Autowired
    PrometheusMetricsService metricsService;

    @Override
    public void configure() throws Exception {

        /*
          TODO: Add support for metadata-headers for easier filtering in Kafka?
                e.g.:
                    - codespace
                    - lineRef
                    - serviceJourneyId
                    - ???
                    - breadcrumbId?
         */


        if (publishEtToKafkaEnabled) {
            log.info("Publishing ET to kafka-topic: {}", kafkaEtTopic);
            from("direct:kafka.et.xml")
                .to("kafka:" + createProducerConfig(kafkaEtTopic))
                .bean(metricsService, "registerAckedKafkaRecord(" + kafkaEtTopic + ")")
                .routeId("anshar.kafka.et.producer");
        } else {
            log.info("Publish ET to kafka disabled");
            AtomicInteger etIgnoredCounter = new AtomicInteger();
            from("direct:kafka.et.xml")
                    .choice().when(p -> etIgnoredCounter.incrementAndGet() % LOG_INTERVAL_ET == 0)
                        .log("Ignore publish to Kafka")
                    .endChoice()
                .routeId("anshar.kafka.et.producer");
        }

        if (publishVmToKafkaEnabled) {
            log.info("Publishing VM to kafka-topic: {}", kafkaVmTopic);
            from("direct:kafka.vm.xml")
                .to("kafka:" + createProducerConfig(kafkaVmTopic))
                .bean(metricsService, "registerAckedKafkaRecord(" + kafkaVmTopic + ")")
                .routeId("anshar.kafka.vm.producer");
        } else {
            log.info("Publish VM to kafka disabled");
            AtomicInteger vmIgnoredCounter = new AtomicInteger();
            from("direct:kafka.vm.xml")
                    .choice().when(p -> vmIgnoredCounter.incrementAndGet() % LOG_INTERVAL_VM == 0)
                        .log("Ignore publish to Kafka")
                    .endChoice()
                .routeId("anshar.kafka.vm.producer");
        }

        if (publishSxToKafkaEnabled) {
            log.info("Publishing SX to kafka-topic: {}", kafkaSxTopic);
            from("direct:kafka.sx.xml")
                .to("kafka:" + createProducerConfig(kafkaSxTopic))
                .bean(metricsService, "registerAckedKafkaRecord(" + kafkaSxTopic + ")")
                .routeId("anshar.kafka.sx.producer");
        } else {
            log.info("Publish SX to kafka disabled");
            AtomicInteger sxIgnoredCounter = new AtomicInteger();
            from("direct:kafka.sx.xml")
                    .choice().when(p -> sxIgnoredCounter.incrementAndGet() % LOG_INTERVAL_SX == 0)
                        .log("Ignore publish to Kafka")
                    .endChoice()
                .routeId("anshar.kafka.sx.producer");
        }
    }
}
