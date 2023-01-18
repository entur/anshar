package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class KafkaAvroPublisherRoute extends RouteBuilder {

    private static final int LOG_INTERVAL_ET = 5000;
    private static final int LOG_INTERVAL_VM = 5000;
    private static final int LOG_INTERVAL_SX = 100;

    @Value("${anshar.kafka.avro.et.enabled:false}")
    protected boolean publishEtAvroToKafkaEnabled;
    @Value("${anshar.kafka.avro.et.topicname:}")
    private String kafkaEtAvroTopic;

    @Value("${anshar.kafka.avro.vm.enabled:false}")
    protected boolean publishVmAvroToKafkaEnabled;
    @Value("${anshar.kafka.avro.vm.topicname:}")
    private String kafkaVmAvroTopic;

    @Value("${anshar.kafka.avro.sx.enabled:false}")
    protected boolean publishSxAvroToKafkaEnabled;
    @Value("${anshar.kafka.avro.sx.topicname:}")
    private String kafkaSxAvroTopic;



    @Autowired
    PrometheusMetricsService metricsService;

    @Autowired
    private KafkaConvertorProcessor kafkaConvertorProcessor;

    @Override
    public void configure() throws Exception {

        if (publishEtAvroToKafkaEnabled) {
            log.info("Publishing Avro-ET to kafka-topic: {}", kafkaEtAvroTopic);
            from("direct:publish.et.avro")
                    .process(kafkaConvertorProcessor)
                    .to("kafka:" + kafkaEtAvroTopic)
                    .bean(metricsService, "registerAckedKafkaRecord(" + kafkaEtAvroTopic + ")")
                    .routeId("anshar.kafka.et.producer.avro");
        } else {
            log.info("Publish Avro-ET to kafka disabled");
            AtomicInteger etIgnoredCounter = new AtomicInteger();
            from("direct:publish.et.avro")
                    .choice().when(p -> etIgnoredCounter.incrementAndGet() % LOG_INTERVAL_ET == 0)
                        .log("Ignore publishing ET to Kafka - counter: " + etIgnoredCounter.get())
                    .endChoice()
                .routeId("anshar.kafka.et.producer.avro");
        }

        if (publishVmAvroToKafkaEnabled) {
            log.info("Publishing Avro-VM to kafka-topic: {}", kafkaVmAvroTopic);
            from("direct:publish.vm.avro")
                    .process(kafkaConvertorProcessor)
                    .to("kafka:" + kafkaVmAvroTopic)
                    .bean(metricsService, "registerAckedKafkaRecord(" + kafkaVmAvroTopic + ")")
                    .routeId("anshar.kafka.vm.producer.avro");
        } else {
            log.info("Publish Avro-VM to kafka disabled");
            AtomicInteger vmIgnoredCounter = new AtomicInteger();
            from("direct:publish.vm.avro")
                    .choice().when(p -> vmIgnoredCounter.incrementAndGet() % LOG_INTERVAL_VM == 0)
                        .log("Ignore publishing VM to Kafka - counter: " + vmIgnoredCounter.get())
                    .endChoice()
                .routeId("anshar.kafka.vm.producer.avro");
        }

        if (publishSxAvroToKafkaEnabled) {
            log.info("Publishing Avro-SX to kafka-topic: {}", kafkaSxAvroTopic);
            from("direct:publish.sx.avro")
                    .process(kafkaConvertorProcessor)
                    .to("kafka:" + kafkaSxAvroTopic)
                    .bean(metricsService, "registerAckedKafkaRecord(" + kafkaSxAvroTopic + ")")
                    .routeId("anshar.kafka.sx.producer.avro");
        } else {
            log.info("Publish Avro-SX to kafka disabled");
            AtomicInteger sxIgnoredCounter = new AtomicInteger();
            from("direct:publish.sx.avro")
                    .choice().when(p -> sxIgnoredCounter.incrementAndGet() % LOG_INTERVAL_SX == 0)
                        .log("Ignore publishing SX to Kafka - counter: " + sxIgnoredCounter.get())
                    .endChoice()
                .routeId("anshar.kafka.sx.producer.avro");
        }
    }
}
