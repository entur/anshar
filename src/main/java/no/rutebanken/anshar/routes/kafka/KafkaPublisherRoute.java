package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaPublisherRoute extends KafkaConfig {

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
                .bean(metricsService, "registerKafkaRecord(" + kafkaEtTopic + ", PrometheusMetricsService.KafkaStatus.ACKED)")
                .routeId("anshar.kafka.et.producer");
        } else {
            log.info("Publish ET to kafka disabled");
            from("direct:kafka.et.xml")
                .log("Ignore publish to Kafka")
                .routeId("anshar.kafka.et.producer");
        }

        if (publishVmToKafkaEnabled) {
            log.info("Publishing VM to kafka-topic: {}", kafkaVmTopic);
            from("direct:kafka.vm.xml")
                .to("kafka:" + createProducerConfig(kafkaVmTopic))
                .bean(metricsService, "registerKafkaRecord(" + kafkaVmTopic + ", PrometheusMetricsService.KafkaStatus.ACKED)")
                .routeId("anshar.kafka.vm.producer");
        } else {
            log.info("Publish VM to kafka disabled");
            from("direct:kafka.vm.xml")
                .log("Ignore publish to Kafka")
                .routeId("anshar.kafka.vm.producer");
        }

        if (publishSxToKafkaEnabled) {
            log.info("Publishing SX to kafka-topic: {}", kafkaSxTopic);
            from("direct:kafka.sx.xml")
                .to("kafka:" + createProducerConfig(kafkaSxTopic))
                .bean(metricsService, "registerKafkaRecord(" + kafkaSxTopic + ", PrometheusMetricsService.KafkaStatus.ACKED)")
                .routeId("anshar.kafka.sx.producer");
        } else {
            log.info("Publish SX to kafka disabled");
            from("direct:kafka.sx.xml")
                .log("Ignore publish to Kafka")
                .routeId("anshar.kafka.sx.producer");
        }
    }
}
