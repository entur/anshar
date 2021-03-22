package no.rutebanken.anshar.routes.kafka;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaPublisherRoute extends RouteBuilder {

    @Value("${anshar.kafka.topic.et.name:}")
    private String kafkaEtTopic;

    @Value("${anshar.kafka.topic.vm.name:}")
    private String kafkaVmTopic;

    @Value("${anshar.kafka.topic.sx.name:}")
    private String kafkaSxTopic;

    @Autowired
    private KafkaPublisher publisher;

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


        from("direct:kafka.et.xml")
            .choice().when(e -> !StringUtils.isEmpty(kafkaEtTopic))
                .setHeader("topic", simple(kafkaEtTopic))
                .bean(publisher, "publishToKafka(${header.topic}, ${body}, ${headers})")
            .endChoice()
            .routeId("anshar.kafka.et.producer");

        from("direct:kafka.vm.xml")
            .choice().when(e -> !StringUtils.isEmpty(kafkaVmTopic))
                .setHeader("topic", simple(kafkaVmTopic))
                .bean(publisher, "publishToKafka(${header.topic}, ${body}, ${headers})")
            .endChoice()
            .routeId("anshar.kafka.vm.producer");

        from("direct:kafka.sx.xml")
            .choice().when(e -> !StringUtils.isEmpty(kafkaSxTopic))
                .setHeader("topic", simple(kafkaSxTopic))
                .bean(publisher, "publishToKafka(${header.topic}, ${body}, ${headers})")
            .endChoice()
            .routeId("anshar.kafka.sx.producer");

    }
}
