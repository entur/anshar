package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.ProducerTemplate;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.LineRef;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SiriTopicPublisherTest {

    private static final String ET_XML = "google-pubsub://test:xml.et";
    private static final String ET_AVRO = "google-pubsub://test:avro.et";
    private static final String SX_XML = "google-pubsub://test:xml.sx";
    private static final String SX_AVRO = "google-pubsub://test:avro.sx";

    /** Keeps what was published, with futures the test completes */
    static class FakeSink implements TopicSink {
        final List<PubsubMessage> messages = new ArrayList<>();
        final List<SettableApiFuture<String>> futures = new ArrayList<>();
        boolean closed;

        @Override
        public synchronized ApiFuture<String> publish(PubsubMessage message) {
            messages.add(message);
            SettableApiFuture<String> future = SettableApiFuture.create();
            futures.add(future);
            return future;
        }

        void succeedAll() {
            futures.forEach(f -> f.set("id"));
        }

        void failAll() {
            futures.forEach(f -> f.setException(new IllegalStateException("Pub/Sub unavailable")));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final Map<String, FakeSink> sinks = new HashMap<>();
    private final Set<String> disabledTopics = new HashSet<>();
    private final TopicSinkFactory sinkFactory = topic -> disabledTopics.contains(topic)
            ? Optional.empty()
            : Optional.of(sinks.computeIfAbsent(topic, t -> new FakeSink()));

    private final PrometheusMetricsService metrics = new PrometheusMetricsService();
    private final ProducerTemplate kafka = mock(ProducerTemplate.class);
    private SiriTopicPublisher publisher;

    @BeforeEach
    void setUp() {
        MDC.put("camel.breadcrumbId", "breadcrumb-1");
    }

    @AfterEach
    void tearDown() {
        MDC.remove("camel.breadcrumbId");
        if (publisher != null) {
            publisher.shutdown();
        }
    }

    private SiriTopicPublisher start(boolean directEnabled) {
        publisher = new SiriTopicPublisher();
        ReflectionTestUtils.setField(publisher, "directEnabled", directEnabled);
        ReflectionTestUtils.setField(publisher, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "etTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "sxTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "etAvroEnabled", true);
        ReflectionTestUtils.setField(publisher, "sxAvroEnabled", true);
        ReflectionTestUtils.setField(publisher, "etXmlTopic", ET_XML);
        ReflectionTestUtils.setField(publisher, "etAvroTopic", ET_AVRO);
        ReflectionTestUtils.setField(publisher, "sxXmlTopic", SX_XML);
        ReflectionTestUtils.setField(publisher, "sxAvroTopic", SX_AVRO);
        ReflectionTestUtils.setField(publisher, "sinkFactory", sinkFactory);
        ReflectionTestUtils.setField(publisher, "metricsService", metrics);
        ReflectionTestUtils.setField(publisher, "siriObjectFactory", new SiriObjectFactory(Instant.now()));
        ReflectionTestUtils.setField(publisher, "mappingAdapterPresets", new MappingAdapterPresets(null));
        ReflectionTestUtils.setField(publisher, "kafkaProducer", kafka);
        // Same thread, so publishAsync has finished when it returns
        ReflectionTestUtils.setField(publisher, "executor", MoreExecutors.newDirectExecutorService());
        publisher.init();
        return publisher;
    }

    private static List<EstimatedVehicleJourney> journeys(int count) {
        return new ArrayList<>(TopicReferenceFixtures.bnrJourneys().subList(0, count));
    }

    private Timer timer(String name, String... tags) {
        return metrics.find(name).tags(tags).timer();
    }

    private Counter failed(String format) {
        return metrics.find("app.anshar.outbound.publish.failed").tags("dataType", "ESTIMATED_TIMETABLE", "format", format).counter();
    }

    @Test
    void publishesOneXmlAndOneAvroMessagePerJourney() {
        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(5), "BNR");

        assertEquals(5, sinks.get(ET_XML).messages.size());
        assertEquals(5, sinks.get(ET_AVRO).messages.size());
        PubsubMessage xml = sinks.get(ET_XML).messages.get(0);
        assertEquals("BNR", xml.getAttributesMap().get("codespaceId"));
        assertEquals("breadcrumb-1", xml.getAttributesMap().get("breadcrumbId"));
        assertTrue(xml.getAttributesMap().containsKey(PrometheusMetricsService.STORED_AT_HEADER));
        assertTrue(sinks.get(ET_AVRO).messages.get(0).getAttributesMap().isEmpty());
    }

    @Test
    void publishesSituations() {
        List<Object> situations = new ArrayList<>(TopicReferenceFixtures.sxDeliveries().get(1).getServiceDelivery()
                .getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements());

        start(true).publishAsync(SiriDataType.SITUATION_EXCHANGE, situations, "ENT");

        assertEquals(3, sinks.get(SX_XML).messages.size());
        assertEquals(3, sinks.get(SX_AVRO).messages.size());
    }

    @Test
    void storedJourneysAreNotChanged() throws Exception {
        List<EstimatedVehicleJourney> journeys = journeys(3);
        LineRef mappedLine = new LineRef();
        mappedLine.setValue("BNR:Line:L13$NSB:Line:L13");
        journeys.get(0).setLineRef(mappedLine);
        String before = SiriXml.toXml(new SiriObjectFactory(Instant.now()).createETServiceDelivery(journeys));

        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys, "BNR");

        String after = SiriXml.toXml(new SiriObjectFactory(Instant.now()).createETServiceDelivery(journeys));
        assertEquals(stripTimestamps(before), stripTimestamps(after));
        assertEquals("BNR:Line:L13$NSB:Line:L13", journeys.get(0).getLineRef().getValue());
    }

    private static String stripTimestamps(String xml) {
        return xml.replaceAll("<(ResponseTimestamp|RecordedAtTime)>[^<]*</\\1>", "");
    }

    @Test
    void appliesOutboundIdMapping() {
        List<EstimatedVehicleJourney> journeys = journeys(1);
        LineRef mappedLine = new LineRef();
        mappedLine.setValue("BNR:Line:L13$NSB:Line:L13");
        journeys.get(0).setLineRef(mappedLine);

        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys, "BNR");

        String xml = sinks.get(ET_XML).messages.get(0).getData().toString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("<LineRef>NSB:Line:L13</LineRef>"), xml);
    }

    @Test
    void successRecordsMetrics() {
        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(5), "BNR");
        sinks.get(ET_XML).succeedAll();
        sinks.get(ET_AVRO).succeedAll();

        assertEquals(5, timer("app.anshar.outbound.publish", "dataType", "ESTIMATED_TIMETABLE", "format", "xml").count());
        assertEquals(5, timer("app.anshar.outbound.publish", "dataType", "ESTIMATED_TIMETABLE", "format", "avro").count());
        assertEquals(5, timer("app.anshar.latency.outbound", "dataType", "ESTIMATED_TIMETABLE").count());
        assertEquals(1, timer("app.anshar.latency.outbound.stage", "dataType", "ESTIMATED_TIMETABLE", "stage", "transformed").count());
        assertEquals(5, timer("app.anshar.latency.outbound.stage", "dataType", "ESTIMATED_TIMETABLE", "stage", "handed_to_client").count());
        assertNull(failed("xml"));
    }

    @Test
    void failedPublishIsCountedNotThrown() {
        start(true);
        assertDoesNotThrow(() -> publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(5), "BNR"));
        sinks.get(ET_XML).failAll();
        sinks.get(ET_AVRO).succeedAll();

        assertEquals(5, failed("xml").count());
        assertNull(failed("avro"));
        assertNull(timer("app.anshar.outbound.publish", "dataType", "ESTIMATED_TIMETABLE", "format", "xml"));
    }

    @Test
    void elementThatFailsToConvertIsCountedForBothFormats() {
        start(true);
        ReflectionTestUtils.setField(publisher, "etConverter",
                (SiriTopicPublisher.Converter) (siri, codespace, breadcrumb, storedAt) -> List.of(
                        new SiriTopicMessages.Failed("broken", new IllegalStateException("no"))));

        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(1), "BNR");

        assertEquals(1, failed("xml").count());
        assertEquals(1, failed("avro").count());
        assertTrue(sinks.get(ET_XML).messages.isEmpty());
    }

    @Test
    void disabledTopicIsSkipped() {
        disabledTopics.add(ET_XML);

        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertFalse(sinks.containsKey(ET_XML));
        assertEquals(2, sinks.get(ET_AVRO).messages.size());
    }

    @Test
    void avroDisabledByItsSwitchIsSkipped() {
        start(true);
        ReflectionTestUtils.setField(publisher, "etAvroEnabled", false);
        publisher.shutdown();
        sinks.clear();
        ReflectionTestUtils.setField(publisher, "executor", MoreExecutors.newDirectExecutorService());
        publisher.init();

        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertEquals(2, sinks.get(ET_XML).messages.size());
        assertFalse(sinks.containsKey(ET_AVRO));
    }

    @Test
    void flagOffPublishesNothing() {
        start(false).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertTrue(sinks.isEmpty());
        assertFalse(publisher.isEnabled());
    }

    @Test
    void vehicleMonitoringIsIgnored() {
        start(true).publishAsync(SiriDataType.VEHICLE_MONITORING, List.of(new Object()), "RUT");

        assertTrue(sinks.values().stream().allMatch(sink -> sink.messages.isEmpty()));
    }

    @Test
    void emptyUpdatesPublishNothing() {
        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, List.of(), "BNR");

        assertTrue(sinks.values().stream().allMatch(sink -> sink.messages.isEmpty()));
        assertNull(timer("app.anshar.latency.outbound.stage", "dataType", "ESTIMATED_TIMETABLE", "stage", "transformed"));
    }

    @Test
    void kafkaGetsRecordAndHeaders() {
        start(true).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        verify(kafka, times(2)).asyncRequestBodyAndHeaders(eq("direct:publish.et.avro.kafka"), any(), anyMap());
        verify(kafka, never()).asyncRequestBodyAndHeaders(eq("direct:publish.sx.avro.kafka"), any(), anyMap());
    }

    @Test
    void connectsToNothingWhenTopicsAreNotPubsubTopics() {
        // As in the test configuration: plain names and mock:ignore, no credentials
        start(true);
        ReflectionTestUtils.setField(publisher, "sinkFactory", new TopicSinkFactory.PubsubTopicSinkFactory());
        ReflectionTestUtils.setField(publisher, "etXmlTopic", "anshar.xml.estimated_timetables");
        ReflectionTestUtils.setField(publisher, "etAvroTopic", "mock:ignore");
        ReflectionTestUtils.setField(publisher, "sxXmlTopic", "");
        ReflectionTestUtils.setField(publisher, "sxAvroTopic", null);
        publisher.shutdown();
        ReflectionTestUtils.setField(publisher, "executor", MoreExecutors.newDirectExecutorService());
        publisher.init();

        for (String sink : new String[]{"etXmlSink", "etAvroSink", "sxXmlSink", "sxAvroSink"}) {
            assertEquals(Optional.empty(), ReflectionTestUtils.getField(publisher, sink), sink);
        }
        assertDoesNotThrow(() -> publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(1), "BNR"));
    }

    @Test
    void closesSinksOnShutdown() {
        start(true).shutdown();

        assertTrue(sinks.values().stream().allMatch(sink -> sink.closed));
        assertEquals(4, sinks.size());
        publisher = null;
    }
}
