package no.rutebanken.anshar.routes.pubsub;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.avro.AvroConvertorProcessor;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each element of a split delivery is published to its XML topic with a blocking Pub/Sub call.
 * Elements must not wait for the publishes of the elements before them, or the last element
 * of a large delivery is delayed by one round trip per element ahead of it.
 * <p>
 * The XML endpoint stubs hold every publish until all elements of the delivery are being
 * published at once, which only happens when they are published in parallel.
 */
class PubsubTopicRouteSplitTest {

    private static final int ELEMENTS = 5;
    private static final long AWAIT_SECONDS = 1;

    private CamelContext context;
    private final PrometheusMetricsService metrics = new PrometheusMetricsService();
    private ProducerTemplate producer;
    private final CountDownLatch etInFlight = new CountDownLatch(ELEMENTS);
    private final CountDownLatch sxInFlight = new CountDownLatch(ELEMENTS);
    /** Publishes that saw every element of the delivery in flight at the same time */
    private final AtomicInteger etConcurrent = new AtomicInteger();
    private final AtomicInteger sxConcurrent = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        PubsubTopicRoute route = new PubsubTopicRoute();
        ReflectionTestUtils.setField(route, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(route, "etTopicXml", "direct:slow.xml.et");
        ReflectionTestUtils.setField(route, "sxTopicXml", "direct:slow.xml.sx");
        ReflectionTestUtils.setField(route, "fmTopicXml", "");
        ReflectionTestUtils.setField(route, "splitPublishThreads", 20);
        ReflectionTestUtils.setField(route, "avroConvertorProcessor", Mockito.mock(AvroConvertorProcessor.class));
        ReflectionTestUtils.setField(route, "metricsService", metrics);

        context = new DefaultCamelContext();
        context.addRoutes(route);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:siri.transform.data").log("transform stub");
                from("direct:slow.xml.et").process(e -> awaitAllInFlight(etInFlight, etConcurrent));
                from("direct:slow.xml.sx").process(e -> awaitAllInFlight(sxInFlight, sxConcurrent));
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

    private static void awaitAllInFlight(CountDownLatch inFlight, AtomicInteger concurrent) throws InterruptedException {
        inFlight.countDown();
        if (inFlight.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
            concurrent.incrementAndGet();
        }
    }

    @Test
    void etJourneysArePublishedWithoutWaitingForEachOther() {
        producer.sendBody("direct:send.to.pubsub.topic.estimated_timetable", etDelivery(ELEMENTS));

        assertEquals(ELEMENTS, etConcurrent.get(), "journeys published while all were in flight");
    }

    @Test
    void sxSituationsArePublishedWithoutWaitingForEachOther() {
        producer.sendBody("direct:send.to.pubsub.topic.situation_exchange", sxDelivery(ELEMENTS));

        assertEquals(ELEMENTS, sxConcurrent.get(), "situations published while all were in flight");
    }

    private Timer stage(String dataType, String stage) {
        return metrics.find("app.anshar.latency.outbound.stage").tags("dataType", dataType, "stage", stage).timer();
    }

    private void assertStageTimedFromStoredTime(String dataType, String stage, int expectedCount) {
        Timer timer = stage(dataType, stage);
        assertNotNull(timer, stage + " not timed");
        assertEquals(expectedCount, timer.count(), stage + " count");
        double meanMillis = timer.mean(TimeUnit.MILLISECONDS);
        assertTrue(meanMillis >= 2_000 && meanMillis < 10_000, stage + " was " + meanMillis + " ms");
    }

    @Test
    void etStagesAreTimedFromTheStoredTime() {
        producer.sendBodyAndHeader("direct:send.to.pubsub.topic.estimated_timetable", etDelivery(ELEMENTS),
                PrometheusMetricsService.STORED_AT_HEADER, System.currentTimeMillis() - 2_000);

        assertStageTimedFromStoredTime("ESTIMATED_TIMETABLE", "route_start", 1);
        assertStageTimedFromStoredTime("ESTIMATED_TIMETABLE", "transformed", 1);
        assertStageTimedFromStoredTime("ESTIMATED_TIMETABLE", "split_ready", 1);
        assertStageTimedFromStoredTime("ESTIMATED_TIMETABLE", "element_start", ELEMENTS);
    }

    @Test
    void sxStagesAreTimedFromTheStoredTime() {
        producer.sendBodyAndHeader("direct:send.to.pubsub.topic.situation_exchange", sxDelivery(ELEMENTS),
                PrometheusMetricsService.STORED_AT_HEADER, System.currentTimeMillis() - 2_000);

        assertStageTimedFromStoredTime("SITUATION_EXCHANGE", "route_start", 1);
        assertStageTimedFromStoredTime("SITUATION_EXCHANGE", "transformed", 1);
        assertStageTimedFromStoredTime("SITUATION_EXCHANGE", "split_ready", 1);
        assertStageTimedFromStoredTime("SITUATION_EXCHANGE", "element_start", ELEMENTS);
    }

    @Test
    void etFirstAndLaterJourneysAreTimedApart() {
        producer.sendBodyAndHeader("direct:send.to.pubsub.topic.estimated_timetable", etDelivery(ELEMENTS),
                PrometheusMetricsService.STORED_AT_HEADER, System.currentTimeMillis() - 2_000);

        assertStageTimedFromStoredTime("ESTIMATED_TIMETABLE", "first_element_start", 1);
        assertStageTimedFromStoredTime("ESTIMATED_TIMETABLE", "later_element_start", ELEMENTS - 1);
    }

    @Test
    void sxFirstAndLaterSituationsAreTimedApart() {
        producer.sendBodyAndHeader("direct:send.to.pubsub.topic.situation_exchange", sxDelivery(ELEMENTS),
                PrometheusMetricsService.STORED_AT_HEADER, System.currentTimeMillis() - 2_000);

        assertStageTimedFromStoredTime("SITUATION_EXCHANGE", "first_element_start", 1);
        assertStageTimedFromStoredTime("SITUATION_EXCHANGE", "later_element_start", ELEMENTS - 1);
    }

    @Test
    void deliveryWithoutStoredTimeIsNotTimed() {
        producer.sendBody("direct:send.to.pubsub.topic.estimated_timetable", etDelivery(ELEMENTS));

        assertNull(stage("ESTIMATED_TIMETABLE", "route_start"));
        assertNull(stage("ESTIMATED_TIMETABLE", "transformed"));
        assertNull(stage("ESTIMATED_TIMETABLE", "split_ready"));
        assertNull(stage("ESTIMATED_TIMETABLE", "element_start"));
        assertNull(stage("ESTIMATED_TIMETABLE", "first_element_start"));
        assertNull(stage("ESTIMATED_TIMETABLE", "later_element_start"));
    }
}
