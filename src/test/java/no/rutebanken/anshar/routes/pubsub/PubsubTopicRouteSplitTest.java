package no.rutebanken.anshar.routes.pubsub;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.avro.AvroConvertorProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each element of a split delivery is published to its XML topic with a blocking Pub/Sub call.
 * Elements must not wait for the publishes of the elements before them, or the last element
 * of a large delivery is delayed by one round trip per element ahead of it.
 */
class PubsubTopicRouteSplitTest {

    private static final int ELEMENTS = 20;
    private static final long PUBLISH_MILLIS = 100;

    private CamelContext context;
    private ProducerTemplate producer;
    private final AtomicInteger etPublished = new AtomicInteger();
    private final AtomicInteger sxPublished = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        PubsubTopicRoute route = new PubsubTopicRoute();
        ReflectionTestUtils.setField(route, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(route, "etTopicXml", "direct:slow.xml.et");
        ReflectionTestUtils.setField(route, "sxTopicXml", "direct:slow.xml.sx");
        ReflectionTestUtils.setField(route, "fmTopicXml", "");
        ReflectionTestUtils.setField(route, "splitPublishThreads", 20);
        ReflectionTestUtils.setField(route, "avroConvertorProcessor", Mockito.mock(AvroConvertorProcessor.class));
        ReflectionTestUtils.setField(route, "metricsService", Mockito.mock(PrometheusMetricsService.class));

        context = new DefaultCamelContext();
        context.addRoutes(route);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:siri.transform.data").log("transform stub");
                from("direct:slow.xml.et").process(e -> {
                    Thread.sleep(PUBLISH_MILLIS);
                    etPublished.incrementAndGet();
                });
                from("direct:slow.xml.sx").process(e -> {
                    Thread.sleep(PUBLISH_MILLIS);
                    sxPublished.incrementAndGet();
                });
                from("direct:publish.et.avro.kafka").log("stub");
                from("direct:publish.et.avro.pubsub").log("stub");
                from("direct:publish.sx.avro.kafka").log("stub");
                from("direct:publish.sx.avro.pubsub").log("stub");
            }
        });
        context.start();
        producer = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private static String etDelivery(int journeys) {
        StringBuilder xml = new StringBuilder("""
                <Siri xmlns="http://www.siri.org.uk/siri" version="2.0"><ServiceDelivery>
                <ResponseTimestamp>2026-11-16T12:00:00+01:00</ResponseTimestamp>
                <EstimatedTimetableDelivery version="2.0"><EstimatedJourneyVersionFrame>
                """);
        for (int i = 0; i < journeys; i++) {
            xml.append("<EstimatedVehicleJourney><DatedVehicleJourneyRef>TST:ServiceJourney:")
                    .append(i).append("</DatedVehicleJourneyRef></EstimatedVehicleJourney>");
        }
        return xml.append("</EstimatedJourneyVersionFrame></EstimatedTimetableDelivery></ServiceDelivery></Siri>").toString();
    }

    private static String sxDelivery(int situations) {
        StringBuilder xml = new StringBuilder("""
                <Siri xmlns="http://www.siri.org.uk/siri" version="2.0"><ServiceDelivery>
                <ResponseTimestamp>2026-11-16T12:00:00+01:00</ResponseTimestamp>
                <SituationExchangeDelivery version="2.0"><Situations>
                """);
        for (int i = 0; i < situations; i++) {
            xml.append("<PtSituationElement><SituationNumber>TST:SituationNumber:")
                    .append(i).append("</SituationNumber></PtSituationElement>");
        }
        return xml.append("</Situations></SituationExchangeDelivery></ServiceDelivery></Siri>").toString();
    }

    @Test
    void etJourneysArePublishedWithoutWaitingForEachOther() {
        long start = System.currentTimeMillis();
        producer.sendBody("direct:send.to.pubsub.topic.estimated_timetable", etDelivery(ELEMENTS));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ELEMENTS, etPublished.get());
        assertTrue(elapsed < ELEMENTS * PUBLISH_MILLIS / 4,
                "publishing " + ELEMENTS + " journeys took " + elapsed + " ms");
    }

    @Test
    void sxSituationsArePublishedWithoutWaitingForEachOther() {
        long start = System.currentTimeMillis();
        producer.sendBody("direct:send.to.pubsub.topic.situation_exchange", sxDelivery(ELEMENTS));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ELEMENTS, sxPublished.get());
        assertTrue(elapsed < ELEMENTS * PUBLISH_MILLIS / 4,
                "publishing " + ELEMENTS + " situations took " + elapsed + " ms");
    }
}
