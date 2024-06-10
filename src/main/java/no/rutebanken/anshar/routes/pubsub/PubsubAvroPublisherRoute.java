package no.rutebanken.anshar.routes.pubsub;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.apache.camel.builder.RouteBuilder;
import org.entur.avro.realtime.siri.model.EstimatedVehicleJourneyRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PubsubAvroPublisherRoute extends RouteBuilder {

    private final AtomicInteger etIgnoredCounter = new AtomicInteger();
    private final AtomicInteger vmIgnoredCounter = new AtomicInteger();
    private final AtomicInteger sxIgnoredCounter = new AtomicInteger();

    @Value("${anshar.pubsub.avro.et.enabled:false}")
    protected boolean publishEtAvroToPubsubEnabled;
    @Value("${anshar.pubsub.avro.et.topicname:}")
    private String pubsubEtAvroTopic;
    @Value("${anshar.pubsub.avro.et.topicname.json:}")
    private String pubsubEtAvroJsonTopic;

    @Value("${anshar.pubsub.avro.vm.enabled:false}")
    protected boolean publishVmAvroToPubsubEnabled;

    @Value("${anshar.pubsub.avro.vm.topicname.json:}")
    private String pubsubVmAvroJsonTopic;

    @Value("${anshar.pubsub.avro.sx.enabled:false}")
    protected boolean publishSxAvroToPubsubEnabled;

    @Value("${anshar.pubsub.avro.sx.topicname.json:}")
    private String pubsubSxAvroJsonTopic;

    @Autowired
    PrometheusMetricsService metricsService;

    @Autowired
    private SiriAvroJsonSerializer siriAvroJsonSerializer;


    @Override
    public void configure() throws Exception {

        if (publishEtAvroToPubsubEnabled) {
            log.info("Publishing Avro-ET to pubsub-topic: {}", pubsubEtAvroTopic);
            from("direct:publish.et.avro.pubsub")
                    .removeHeaders("*")
                    .wireTap("direct:publish.et.avro.pubsub.bin")
                    .wireTap("direct:publish.et.avro.pubsub.json")
                    .bean(metricsService, "registerAvroPubsubRecord(ESTIMATED_TIMETABLE)")
            ;

            from("direct:publish.et.avro.pubsub.bin")
                    .process(p -> {
                        EstimatedVehicleJourneyRecord body = p.getMessage().getBody(EstimatedVehicleJourneyRecord.class);
                        p.getMessage().setBody(body.toByteBuffer().array());
                    })
                    .to(pubsubEtAvroTopic)
                    .routeId("anshar.pubsub.et.producer.avro.bin");

            from("direct:publish.et.avro.pubsub.json")
                    .choice().when().constant(!pubsubEtAvroJsonTopic.equals("mock:ignore"))
                        .process(siriAvroJsonSerializer)
                        .to(pubsubEtAvroJsonTopic)
                    .endChoice()
                    .routeId("anshar.pubsub.et.producer.avro.json");
        } else {
            log.info("Publish Avro-ET to pubsub disabled");
            from("direct:publish.et.avro.pubsub")
                    .process(p -> {
                        if (etIgnoredCounter.incrementAndGet() % 1000 == 0) {
                            p.getMessage().setHeader("counter", etIgnoredCounter.get());
                            p.getMessage().setBody(p.getIn().getBody());
                        }
                    })
                    .choice()
                    .when(header("counter").isNotNull())
                        .log("Ignore publishing ET to Pubsub - counter: ${header.counter}")
                    .endChoice()
                    .end()
                .routeId("anshar.pubsub.et.producer.avro.pubsub");
        }

        if (publishVmAvroToPubsubEnabled) {
            log.info("Publishing Avro-VM to pubsub-topic: {}", pubsubVmAvroJsonTopic);
            from("direct:publish.vm.avro.pubsub")
                    .removeHeaders("*")
                    .choice().when().constant(!pubsubVmAvroJsonTopic.equals("mock:ignore"))
                        .process(siriAvroJsonSerializer)
                        .to(pubsubVmAvroJsonTopic)
                    .end()
                    .bean(metricsService, "registerAvroPubsubRecord(VEHICLE_MONITORING)")
                    .routeId("anshar.pubsub.vm.producer.avro.pubsub");

        } else {
            log.info("Publish Avro-VM to pubsub disabled");
            from("direct:publish.vm.avro.pubsub")
                    .process(p -> {
                        if (vmIgnoredCounter.incrementAndGet() % 1000 == 0) {
                            p.getMessage().setHeader("counter", vmIgnoredCounter.get());
                            p.getMessage().setBody(p.getIn().getBody());
                        }
                    })
                    .choice()
                    .when(header("counter").isNotNull())
                        .log("Ignore publishing VM to Pubsub - counter: ${header.counter}")
                    .end()
                .routeId("anshar.pubsub.vm.producer.avro.pubsub");
        }

        if (publishSxAvroToPubsubEnabled) {
            log.info("Publishing Avro-SX to pubsub-topic: {}", pubsubSxAvroJsonTopic);

            from("direct:publish.sx.avro.pubsub")
                    .removeHeaders("*")
                    .choice().when().constant(!pubsubSxAvroJsonTopic.equals("mock:ignore"))
                        .process(siriAvroJsonSerializer)
                        .to(pubsubSxAvroJsonTopic)
                    .end()
                    .bean(metricsService, "registerAvroPubsubRecord(SITUATION_EXCHANGE)")
                    .routeId("anshar.pubsub.sx.producer.avro.pubsub");

        } else {
            log.info("Publish Avro-SX to pubsub disabled");
            from("direct:publish.sx.avro.pubsub")
                    .process(p -> {
                        if (sxIgnoredCounter.incrementAndGet() % 1000 == 0) {
                            p.getMessage().setHeader("counter", sxIgnoredCounter.get());
                            p.getMessage().setBody(p.getIn().getBody());
                        }
                    })
                    .choice()
                    .when(header("counter").isNotNull())
                    .log("Ignore publishing SX to Pubsub - counter: ${header.counter}")
                    .endChoice()
                    .end()
                .routeId("anshar.pubsub.sx.producer.avro.pubsub");
        }
    }
}
