package no.rutebanken.anshar.routes.pubsub;

import no.rutebanken.anshar.routes.avro.AvroConvertorProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.entur.avro.realtime.siri.model.SiriRecord;
import org.entur.siri21.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.entur.avro.realtime.siri.converter.Converter.avro2Jaxb;
import static org.entur.avro.realtime.siri.converter.Converter.jaxb2Avro;

@Service
public class PubsubTopicRoute extends RouteBuilder {


    @Value("${anshar.outbound.camel.route.topic.et.name.xml:}")
    private String etTopicXml;

    @Value("${anshar.outbound.camel.route.topic.sx.name.xml:}")
    private String sxTopicXml;

    @Value("${anshar.outbound.pubsub.topic.enabled}")
    private boolean pushToTopicEnabled;

    @Autowired
    private AvroConvertorProcessor avroConvertorProcessor;

    @Override
    public void configure() {
        if (pushToTopicEnabled) {

            /**
             * Splits SIRI ET-ServiceDelivery into singular messages (i.e. one ET-message per ServiceDelivery), converts
             * message to protobuf, and posts to Cloud Pubsub
             */
            from("direct:send.to.pubsub.topic.estimated_timetable")
                    .to("direct:siri.transform.data")
                    .choice().when(body().isNotNull())
                        .to("xslt-saxon:xsl/split.xsl")
                        .split().tokenizeXML("Siri").streaming()
                        .wireTap("direct:publish.et.avro")        // Publish as Avro
                        .to("direct:publish.et.xml")        // Publish as XML
                    .end()
            ;

            /**
             * Splits SIRI VM-ServiceDelivery into singular messages (i.e. one VM-message per ServiceDelivery), converts
             * message to protobuf, and posts to Cloud Pubsub
             */
            from("direct:send.to.pubsub.topic.vehicle_monitoring")
                    .to("direct:siri.transform.data")
                    .choice().when(body().isNotNull())
                        .process(p -> {
                            try {
                                p.getMessage().setBody(SiriXml.toXml(p.getIn().getBody(Siri.class)));
                            } catch (NullPointerException e) {
                                try {
                                    SiriRecord siriRecord = jaxb2Avro(p.getIn().getBody(Siri.class));
                                    Siri siri = avro2Jaxb(siriRecord);
                                    p.getMessage().setBody(SiriXml.toXml(siri));
                                } catch (NullPointerException e2) {
                                    log.error("Caught NullPointerException twice - giving up.", e2);
                                }
                            }
                        })
                        .to("xslt-saxon:xsl/split.xsl")
                        .split().tokenizeXML("Siri").streaming()
                        .to("direct:publish.vm.avro")// Publish as Avro
                    .end()
            ;

            /**
             * Splits SIRI SX-ServiceDelivery into singular messages (i.e. one SX-message per ServiceDelivery), converts
             * message to protobuf, and posts to Cloud Pubsub
             */
            from("direct:send.to.pubsub.topic.situation_exchange")
                    .to("direct:siri.transform.data")
                    .choice().when(body().isNotNull())
                        .to("xslt-saxon:xsl/split.xsl")
                        .split().tokenizeXML("Siri").streaming()
                        .wireTap("direct:publish.sx.avro")// Publish as Avro
                        .to("direct:publish.sx.xml")        // Publish as XML
                    .end()
            ;

            if (etTopicXml != null) {
                from("direct:publish.et.xml")
                        .to(etTopicXml)
                ;
            } else {
                AtomicBoolean etTopicXmlWarned = new AtomicBoolean(false);
                from("direct:publish.et.xml")
                        .process(p -> {
                            if (!etTopicXmlWarned.get()) {
                                log.warn("No XML topic defined for ET. Skipping XML publish.");
                                etTopicXmlWarned.set(true);
                            }
                        })
                ;
            }

            if (sxTopicXml != null) {
                from("direct:publish.sx.xml")
                        .to(sxTopicXml)
                ;
            } else {
                AtomicBoolean sxTopicXmlWarned = new AtomicBoolean(false);
                from("direct:publish.sx.xml")
                        .process(p -> {
                            if (!sxTopicXmlWarned.get()) {
                                log.warn("No XML topic defined for ET. Skipping XML publish.");
                                sxTopicXmlWarned.set(true);
                            }
                        })
                ;
            }


            from("direct:publish.et.avro")
                    .process(avroConvertorProcessor)
                    .wireTap("direct:publish.et.avro.kafka")
                    .wireTap("direct:publish.et.avro.pubsub")
            ;

            from("direct:publish.vm.avro")
                    .process(avroConvertorProcessor)
                    .wireTap("direct:publish.vm.avro.kafka")
                    .wireTap("direct:publish.vm.avro.pubsub")
            ;

            from("direct:publish.sx.avro")
                    .process(avroConvertorProcessor)
                    .wireTap("direct:publish.sx.avro.kafka")
                    .wireTap("direct:publish.sx.avro.pubsub")
            ;
        }
    }
}

