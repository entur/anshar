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
import uk.org.siri.siri21.Siri;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        public void startShutdown() {
            closed = true;
        }

        @Override
        public boolean awaitShutdown(java.time.Duration timeout) {
            return true;
        }
    }

    private final Map<String, FakeSink> sinks = new HashMap<>();
    private final Set<String> notPubsubTopics = new HashSet<>();
    private TopicSinkFactory sinkFactory = topic -> notPubsubTopics.contains(topic)
            ? Optional.empty()
            : Optional.of(sinks.computeIfAbsent(topic, t -> new FakeSink()));

    private final PrometheusMetricsService metrics = new PrometheusMetricsService();
    private final ProducerTemplate kafka = mock(ProducerTemplate.class);
    private SiriTopicPublisher.Converter etConverter = SiriTopicMessages::estimatedTimetableElements;
    private SiriTopicPublisher publisher;

    @BeforeEach
    void setUp() {
        MDC.put("camel.breadcrumbId", "breadcrumb-1");
        when(kafka.asyncRequestBodyAndHeaders(anyString(), any(), anyMap())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() {
        MDC.remove("camel.breadcrumbId");
        if (publisher != null) {
            publisher.shutdown();
        }
    }

    /** Everything on, publishing on the calling thread so publishAsync has finished when it returns */
    private SiriTopicPublisher start() {
        return start(sameThread(p -> { }));
    }

    private SiriTopicPublisher start(Consumer<SiriTopicPublisher> configure) {
        publisher = new SiriTopicPublisher(sinkFactory, metrics, new SiriObjectFactory(Instant.now()),
                new MappingAdapterPresets(null), etConverter, SiriTopicMessages::situationElements);
        ReflectionTestUtils.setField(publisher, "directEnabled", true);
        ReflectionTestUtils.setField(publisher, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "etTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "sxTopicEnabled", true);
        ReflectionTestUtils.setField(publisher, "etAvroEnabled", true);
        ReflectionTestUtils.setField(publisher, "sxAvroEnabled", true);
        ReflectionTestUtils.setField(publisher, "etKafkaEnabled", true);
        ReflectionTestUtils.setField(publisher, "sxKafkaEnabled", true);
        ReflectionTestUtils.setField(publisher, "etXmlTopic", ET_XML);
        ReflectionTestUtils.setField(publisher, "etAvroTopic", ET_AVRO);
        ReflectionTestUtils.setField(publisher, "sxXmlTopic", SX_XML);
        ReflectionTestUtils.setField(publisher, "sxAvroTopic", SX_AVRO);
        ReflectionTestUtils.setField(publisher, "threads", 1);
        ReflectionTestUtils.setField(publisher, "buildThreads", 2);
        ReflectionTestUtils.setField(publisher, "queueSize", 10);
        ReflectionTestUtils.setField(publisher, "drainTimeoutMillis", 10_000L);
        ReflectionTestUtils.setField(publisher, "kafkaProducer", kafka);
        configure.accept(publisher);
        publisher.init();
        return publisher;
    }

    private static Consumer<SiriTopicPublisher> sameThread(Consumer<SiriTopicPublisher> more) {
        return p -> {
            ReflectionTestUtils.setField(p, "executor", MoreExecutors.newDirectExecutorService());
            ReflectionTestUtils.setField(p, "buildExecutor", MoreExecutors.newDirectExecutorService());
            more.accept(p);
        };
    }

    private static List<EstimatedVehicleJourney> journeys(int count) {
        return new ArrayList<>(TopicReferenceFixtures.bnrJourneys().subList(0, count));
    }

    private Timer timer(String name, String... tags) {
        return metrics.find(name).tags(tags).timer();
    }

    private Counter failed(String format) {
        // xml, avro or kafka
        return metrics.find("app.anshar.outbound.publish.failed").tags("dataType", "ESTIMATED_TIMETABLE", "format", format).counter();
    }

    private static double count(Counter counter) {
        return counter == null ? 0 : counter.count();
    }

    /** Messages for each journey, with the XML or the Avro part failed as asked */
    private static SiriTopicPublisher.Converter withFailed(boolean xmlFails, boolean avroFails) {
        return (siri, codespace, breadcrumb, storedAt) -> SiriTopicMessages.estimatedTimetableElements(siri, codespace, breadcrumb, storedAt)
                .stream()
                .map(element -> (Supplier<ElementMessages>) () -> {
                    ElementMessages m = element.get();
                    return new ElementMessages(m.elementId(),
                            xmlFails ? null : m.xml(), xmlFails ? new IllegalStateException("no xml") : null,
                            avroFails ? null : m.avro(), avroFails ? new IllegalStateException("no avro") : null);
                })
                .toList();
    }

    @Test
    void publishesOneXmlAndOneAvroMessagePerJourney() {
        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(5), "BNR");

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

        start().publishAsync(SiriDataType.SITUATION_EXCHANGE, situations, "ENT");

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

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys, "BNR");

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

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys, "BNR");

        String xml = sinks.get(ET_XML).messages.get(0).getData().toString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("<LineRef>NSB:Line:L13</LineRef>"), xml);
    }

    @Test
    void successRecordsMetrics() {
        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(5), "BNR");
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
        start();
        assertDoesNotThrow(() -> publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(5), "BNR"));
        sinks.get(ET_XML).failAll();
        sinks.get(ET_AVRO).succeedAll();

        assertEquals(5, failed("xml").count());
        assertNull(failed("avro"));
        assertNull(timer("app.anshar.outbound.publish", "dataType", "ESTIMATED_TIMETABLE", "format", "xml"));
    }

    @Test
    void avroFailureStillPublishesXml() {
        etConverter = withFailed(false, true);

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertEquals(2, sinks.get(ET_XML).messages.size());
        assertTrue(sinks.get(ET_AVRO).messages.isEmpty());
        assertNull(failed("xml"));
        assertEquals(2, failed("avro").count());
        assertEquals(2, failed("kafka").count());
        verify(kafka, never()).asyncRequestBodyAndHeaders(anyString(), any(), anyMap());
    }

    @Test
    void xmlFailureStillPublishesAvroAndKafka() {
        etConverter = withFailed(true, false);

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertTrue(sinks.get(ET_XML).messages.isEmpty());
        assertEquals(2, sinks.get(ET_AVRO).messages.size());
        assertEquals(2, failed("xml").count());
        assertNull(failed("avro"));
        verify(kafka, times(2)).asyncRequestBodyAndHeaders(eq("direct:publish.et.avro.kafka"), any(), anyMap());
    }

    @Test
    void failuresAreCountedOnlyForTopicsInUse() {
        etConverter = withFailed(true, true);

        start(sameThread(p -> {
            ReflectionTestUtils.setField(p, "etAvroEnabled", false);
            ReflectionTestUtils.setField(p, "etKafkaEnabled", false);
        })).publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertEquals(2, failed("xml").count());
        assertNull(failed("avro"));
        assertNull(failed("kafka"));
    }

    @Test
    void deliveryThatFailsBeforeConversionIsCountedForEveryTopic() {
        etConverter = (siri, codespace, breadcrumb, storedAt) -> {
            throw new IllegalStateException("broken delivery");
        };

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(3), "BNR");

        assertEquals(3, failed("xml").count());
        assertEquals(3, failed("avro").count());
        assertEquals(3, failed("kafka").count());
    }

    @Test
    void eachJourneyIsSentBeforeTheNextIsBuilt() {
        List<Integer> sentBeforeEach = new ArrayList<>();
        etConverter = (siri, codespace, breadcrumb, storedAt) -> SiriTopicMessages.estimatedTimetableElements(
                siri, codespace, breadcrumb, storedAt).stream()
                .map(element -> (Supplier<ElementMessages>) () -> {
                    sentBeforeEach.add(sinks.get(ET_XML).messages.size());
                    return element.get();
                })
                .toList();

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(3), "BNR");

        assertEquals(List.of(0, 1, 2), sentBeforeEach);
    }

    @Test
    void failureMidDeliveryCountsOnlyTheJourneysNotSent() {
        etConverter = (siri, codespace, breadcrumb, storedAt) -> {
            List<Supplier<ElementMessages>> elements = new ArrayList<>(
                    SiriTopicMessages.estimatedTimetableElements(siri, codespace, breadcrumb, storedAt));
            elements.set(1, () -> {
                throw new IllegalStateException("broken journey");
            });
            return elements;
        };

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(3), "BNR");

        assertEquals(2, sinks.get(ET_XML).messages.size());
        assertEquals(1, failed("xml").count());
        assertEquals(1, failed("avro").count());
        assertEquals(1, failed("kafka").count());
    }

    @Test
    void journeysOfADeliveryAreBuiltInParallel() throws Exception {
        // Each build waits until the other has started: only possible when they run at the same time
        CountDownLatch bothBuilding = new CountDownLatch(2);
        List<Boolean> sawTheOther = java.util.Collections.synchronizedList(new ArrayList<>());
        etConverter = (siri, codespace, breadcrumb, storedAt) -> SiriTopicMessages.estimatedTimetableElements(
                siri, codespace, breadcrumb, storedAt).stream()
                .map(element -> (Supplier<ElementMessages>) () -> {
                    bothBuilding.countDown();
                    try {
                        sawTheOther.add(bothBuilding.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return element.get();
                })
                .toList();
        start(p -> { });

        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");
        publisher.shutdown();
        publisher = null;

        assertEquals(List.of(true, true), sawTheOther);
        assertEquals(2, sinks.get(ET_XML).messages.size());
    }

    @Test
    void publishesACopyNeverTheStoredObjects() {
        List<Siri> converted = new ArrayList<>();
        etConverter = (siri, codespace, breadcrumb, storedAt) -> {
            converted.add(siri);
            return SiriTopicMessages.estimatedTimetableElements(siri, codespace, breadcrumb, storedAt);
        };
        List<EstimatedVehicleJourney> journeys = journeys(1);

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys, "BNR");

        EstimatedVehicleJourney published = converted.get(0).getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0);
        assertNotSame(journeys.get(0), published, "the stored journey itself was handed to the converter");
    }

    @Test
    void typeStaysOnTheCamelRouteWhenItsXmlTopicIsNotAPubsubTopic() {
        notPubsubTopics.add(ET_XML);

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertFalse(publisher.handles(SiriDataType.ESTIMATED_TIMETABLE));
        assertTrue(publisher.handles(SiriDataType.SITUATION_EXCHANGE));
        assertFalse(sinks.containsKey(ET_XML));
        assertFalse(sinks.containsKey(ET_AVRO));
    }

    @Test
    void avroDisabledByItsSwitchIsSkipped() {
        start(sameThread(p -> ReflectionTestUtils.setField(p, "etAvroEnabled", false)))
                .publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertEquals(2, sinks.get(ET_XML).messages.size());
        assertFalse(sinks.containsKey(ET_AVRO));
    }

    @Test
    void avroRecordsAreCountedWhenTheAvroTopicIsIgnored() {
        // anshar.pubsub.avro.et.topicname.json=mock:ignore, as the Camel Avro route counted it
        notPubsubTopics.add(ET_AVRO);

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        Counter avroRecords = metrics.find("app.anshar.data.outbound").tags("dataType", "ESTIMATED_TIMETABLE").counter();
        assertEquals(2, count(avroRecords));
        assertNull(failed("avro"));
    }

    @Test
    void flagOffPublishesNothing() {
        start(sameThread(p -> ReflectionTestUtils.setField(p, "directEnabled", false)))
                .publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertTrue(sinks.isEmpty());
        assertFalse(publisher.handles(SiriDataType.ESTIMATED_TIMETABLE));
    }

    @Test
    void topicPublishingOffMeansNothingIsHandled() {
        start(sameThread(p -> ReflectionTestUtils.setField(p, "pushToTopicEnabled", false)));

        assertFalse(publisher.handles(SiriDataType.ESTIMATED_TIMETABLE));
        assertFalse(publisher.handles(SiriDataType.SITUATION_EXCHANGE));
    }

    @Test
    void vehicleMonitoringIsIgnored() {
        start().publishAsync(SiriDataType.VEHICLE_MONITORING, List.of(new Object()), "RUT");

        assertFalse(publisher.handles(SiriDataType.VEHICLE_MONITORING));
        assertTrue(sinks.values().stream().allMatch(sink -> sink.messages.isEmpty()));
    }

    @Test
    void emptyUpdatesPublishNothing() {
        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, List.of(), "BNR");

        assertTrue(sinks.values().stream().allMatch(sink -> sink.messages.isEmpty()));
        assertNull(timer("app.anshar.latency.outbound.stage", "dataType", "ESTIMATED_TIMETABLE", "stage", "transformed"));
    }

    @Test
    void kafkaGetsRecordAndHeaders() {
        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        verify(kafka, times(2)).asyncRequestBodyAndHeaders(eq("direct:publish.et.avro.kafka"), any(), anyMap());
        verify(kafka, never()).asyncRequestBodyAndHeaders(eq("direct:publish.sx.avro.kafka"), any(), anyMap());
    }

    @Test
    void connectsToNothingWhenTopicsAreNotPubsubTopics() {
        // As in the test configuration: plain names and mock:ignore, no credentials
        sinkFactory = new PubsubTopicSinkFactory();
        start(sameThread(p -> {
            ReflectionTestUtils.setField(p, "etXmlTopic", "anshar.xml.estimated_timetables");
            ReflectionTestUtils.setField(p, "etAvroTopic", "mock:ignore");
            ReflectionTestUtils.setField(p, "sxXmlTopic", "");
            ReflectionTestUtils.setField(p, "sxAvroTopic", null);
        }));

        assertFalse(publisher.handles(SiriDataType.ESTIMATED_TIMETABLE));
        assertFalse(publisher.handles(SiriDataType.SITUATION_EXCHANGE));
        assertDoesNotThrow(() -> publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(1), "BNR"));
    }

    @Test
    void kafkaFailureIsCountedAndOtherElementsStillPublished() {
        // As when Camel has already stopped while the publisher drains its queue
        when(kafka.asyncRequestBodyAndHeaders(anyString(), any(), anyMap()))
                .thenThrow(new IllegalStateException("ProducerTemplate has not been started"));

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(3), "BNR");

        assertEquals(3, sinks.get(ET_XML).messages.size());
        assertEquals(3, sinks.get(ET_AVRO).messages.size());
        assertEquals(3, failed("kafka").count());
    }

    @Test
    void kafkaFailureInsideTheRouteIsCounted() {
        when(kafka.asyncRequestBodyAndHeaders(anyString(), any(), anyMap()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        start().publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertEquals(2, failed("kafka").count());
    }

    @Test
    void kafkaFailuresAreNotCountedWhenKafkaIsOff() {
        when(kafka.asyncRequestBodyAndHeaders(anyString(), any(), anyMap()))
                .thenThrow(new IllegalStateException("ProducerTemplate has not been started"));

        start(sameThread(p -> ReflectionTestUtils.setField(p, "etKafkaEnabled", false)))
                .publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertNull(failed("kafka"));
    }

    /** Blocks every publish until released, as the Pub/Sub client does when flow control is exhausted */
    static class BlockingSink extends FakeSink {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch blocked = new CountDownLatch(1);

        @Override
        public ApiFuture<String> publish(PubsubMessage message) {
            blocked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.publish(message);
        }
    }

    @Test
    void fullQueueRejectsAndCountsInsteadOfGrowing() throws Exception {
        BlockingSink blocking = new BlockingSink();
        sinks.put(ET_XML, blocking);
        // A real bounded pool: one thread, room for one waiting delivery
        start(p -> ReflectionTestUtils.setField(p, "queueSize", 1));

        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR"); // runs, blocks in the sink
        assertTrue(blocking.blocked.await(5, TimeUnit.SECONDS));
        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR"); // waits in the queue
        assertDoesNotThrow(() -> publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR")); // no room

        assertEquals(2, failed("xml").count());
        assertEquals(2, failed("avro").count());
        assertEquals(2, failed("kafka").count());
        assertEquals(1, metrics.find("app.anshar.outbound.publisher.queue").gauge().value());
        blocking.release.countDown();
    }

    @Test
    void updatesArrivingAfterShutdownAreCountedNotThrown() {
        start(p -> { });
        publisher.shutdown();

        assertDoesNotThrow(() -> publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR"));
        assertEquals(2, failed("xml").count());
        publisher = null;
    }

    @Test
    void updatesStillQueuedWhenShutdownGivesUpAreCounted() throws Exception {
        BlockingSink blocking = new BlockingSink();
        sinks.put(ET_XML, blocking);
        start(p -> ReflectionTestUtils.setField(p, "drainTimeoutMillis", 0L));

        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR"); // runs, blocks in the sink
        assertTrue(blocking.blocked.await(5, TimeUnit.SECONDS));
        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR"); // queued
        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR"); // queued

        publisher.shutdown();
        publisher = null;

        assertEquals(4, failed("xml").count());
        blocking.release.countDown();
    }

    @Test
    void sinkCreationFailureClosesTheSinksAlreadyCreated() {
        FakeSink etXml = new FakeSink();
        sinkFactory = topic -> {
            if (topic.equals(ET_AVRO)) {
                throw new IllegalStateException("no credentials");
            }
            return Optional.of(topic.equals(ET_XML) ? etXml : new FakeSink());
        };

        assertThrows(IllegalStateException.class, () -> start());
        publisher = null;

        assertTrue(etXml.closed);
    }

    @Test
    void droppedUpdatesAreCountedOnlyForTopicsInUse() {
        start(p -> {
            ReflectionTestUtils.setField(p, "etAvroEnabled", false);
            ReflectionTestUtils.setField(p, "etKafkaEnabled", false);
        });
        publisher.shutdown();

        publisher.publishAsync(SiriDataType.ESTIMATED_TIMETABLE, journeys(2), "BNR");

        assertEquals(2, failed("xml").count());
        assertNull(failed("avro"));
        assertNull(failed("kafka"));
        publisher = null;
    }

    @Test
    void sinkCreationFailureForSituationsClosesTheJourneySinks() {
        sinkFactory = topic -> {
            if (topic.equals(SX_XML)) {
                throw new IllegalStateException("no credentials");
            }
            return Optional.of(sinks.computeIfAbsent(topic, t -> new FakeSink()));
        };

        assertThrows(IllegalStateException.class, () -> start());
        publisher = null;

        assertTrue(sinks.get(ET_XML).closed);
        assertTrue(sinks.get(ET_AVRO).closed);
    }

    @Test
    void sinksAreShutDownTogetherBeforeAnyIsAwaited() {
        List<String> events = new ArrayList<>();
        for (String topic : List.of(ET_XML, ET_AVRO, SX_XML, SX_AVRO)) {
            sinks.put(topic, new FakeSink() {
                @Override
                public void startShutdown() {
                    events.add("shutdown " + topic);
                }

                @Override
                public boolean awaitShutdown(java.time.Duration timeout) {
                    events.add("await " + topic);
                    return true;
                }
            });
        }
        start().shutdown();
        publisher = null;

        assertEquals(8, events.size());
        assertTrue(events.subList(0, 4).stream().allMatch(e -> e.startsWith("shutdown")), events.toString());
    }

    @Test
    void closesSinksOnShutdown() {
        start().shutdown();

        assertTrue(sinks.values().stream().allMatch(sink -> sink.closed));
        assertEquals(4, sinks.size());
        publisher = null;
    }
}
