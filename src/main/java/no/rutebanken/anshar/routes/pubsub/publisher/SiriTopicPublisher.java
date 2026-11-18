package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes stored ET journeys and SX situations to the outbound XML and Avro-JSON Pub/Sub topics, one message per
 * element, without waiting for Pub/Sub to answer. Replaces the Camel topic routes for ET and SX when
 * {@code anshar.outbound.pubsub.direct.enabled} is on; the Kafka publish still goes through its Camel route.
 * A data type whose XML topic is not a Pub/Sub topic stays on the Camel route (see {@link #handles}).
 */
@Service
public class SiriTopicPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SiriTopicPublisher.class);

    static final String XML = "xml";
    static final String AVRO = "avro";
    static final String KAFKA = "kafka";
    private static final String BREADCRUMB_MDC_KEY = "camel.breadcrumbId";
    private static final String QUEUE_GAUGE_NAME = "app.anshar.outbound.publisher.queue";
    private static final Duration SINKS_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    /** Builds the messages for a mapped delivery; see {@link SiriTopicMessages} */
    @FunctionalInterface
    interface Converter {
        List<ElementMessages> convert(Siri mappedDelivery, String codespace, String breadcrumbId, long storedAt);
    }

    /**
     * Where one data type is published.
     *
     * @param avroSink     null when the Avro-JSON topic is off or not a Pub/Sub topic
     * @param avroEnabled  whether Avro records are counted as published, as the Camel Avro route counted them
     * @param kafkaEnabled whether the Kafka route publishes, so its failures count
     */
    private record TypeRoute(SiriDataType dataType, Converter converter, TopicSink xmlSink, TopicSink avroSink,
                             boolean avroEnabled, boolean kafkaEnabled, String kafkaEndpoint) {
    }

    /** The work for one delivery, which knows what to count when it cannot run */
    private record PublishTask(TypeRoute route, int elements, String codespace, Runnable work) implements Runnable {
        @Override
        public void run() {
            work.run();
        }
    }

    @Value("${anshar.outbound.pubsub.direct.enabled:false}")
    private boolean directEnabled;

    // The switches the Camel topic routes obey, so turning direct publishing on publishes exactly what they did
    @Value("${anshar.outbound.pubsub.topic.enabled:false}")
    private boolean pushToTopicEnabled;
    @Value("${anshar.outbound.pubsub.et.topic.enabled:false}")
    private boolean etTopicEnabled;
    @Value("${anshar.outbound.pubsub.sx.topic.enabled:false}")
    private boolean sxTopicEnabled;
    @Value("${anshar.pubsub.avro.et.enabled:false}")
    private boolean etAvroEnabled;
    @Value("${anshar.pubsub.avro.sx.enabled:false}")
    private boolean sxAvroEnabled;
    @Value("${anshar.kafka.avro.et.enabled:false}")
    private boolean etKafkaEnabled;
    @Value("${anshar.kafka.avro.sx.enabled:false}")
    private boolean sxKafkaEnabled;

    @Value("${anshar.outbound.camel.route.topic.et.name.xml:}")
    private String etXmlTopic;
    @Value("${anshar.pubsub.avro.et.topicname.json:}")
    private String etAvroTopic;
    @Value("${anshar.outbound.camel.route.topic.sx.name.xml:}")
    private String sxXmlTopic;
    @Value("${anshar.pubsub.avro.sx.topicname.json:}")
    private String sxAvroTopic;

    @Value("${anshar.outbound.pubsub.publisher.threads:4}")
    private int threads;

    /** Deliveries waiting for a publisher thread; beyond this they are dropped and counted, not queued */
    @Value("${anshar.outbound.pubsub.publisher.queue.size:10000}")
    private int queueSize;

    /** How long a stopping pod lets queued deliveries drain before dropping (and counting) the rest */
    @Value("${anshar.outbound.pubsub.publisher.drain.timeout.ms:10000}")
    private long drainTimeoutMillis;

    @Produce
    private ProducerTemplate kafkaProducer;

    private final TopicSinkFactory sinkFactory;
    private final PrometheusMetricsService metricsService;
    private final SiriObjectFactory siriObjectFactory;
    private final MappingAdapterPresets mappingAdapterPresets;
    private final Converter etConverter;
    private final Converter sxConverter;

    private final Map<SiriDataType, TypeRoute> routes = new EnumMap<>(SiriDataType.class);
    private final AtomicLong rejectedDeliveries = new AtomicLong();
    private ExecutorService executor;

    @Autowired
    public SiriTopicPublisher(TopicSinkFactory sinkFactory, PrometheusMetricsService metricsService,
                              SiriObjectFactory siriObjectFactory, MappingAdapterPresets mappingAdapterPresets) {
        this(sinkFactory, metricsService, siriObjectFactory, mappingAdapterPresets,
                SiriTopicMessages::forEstimatedTimetables, SiriTopicMessages::forSituations);
    }

    SiriTopicPublisher(TopicSinkFactory sinkFactory, PrometheusMetricsService metricsService,
                       SiriObjectFactory siriObjectFactory, MappingAdapterPresets mappingAdapterPresets,
                       Converter etConverter, Converter sxConverter) {
        this.sinkFactory = sinkFactory;
        this.metricsService = metricsService;
        this.siriObjectFactory = siriObjectFactory;
        this.mappingAdapterPresets = mappingAdapterPresets;
        this.etConverter = etConverter;
        this.sxConverter = sxConverter;
    }

    @PostConstruct
    void init() {
        if (!directEnabled) {
            return;
        }
        if (!pushToTopicEnabled) {
            logger.warn("anshar.outbound.pubsub.direct.enabled is on, but anshar.outbound.pubsub.topic.enabled is off: nothing is published to the topics");
            return;
        }
        try {
            if (etTopicEnabled) {
                addRoute(route(SiriDataType.ESTIMATED_TIMETABLE, etConverter, etXmlTopic, etAvroEnabled, etAvroTopic,
                        etKafkaEnabled, "direct:publish.et.avro.kafka"));
            }
            if (sxTopicEnabled) {
                addRoute(route(SiriDataType.SITUATION_EXCHANGE, sxConverter, sxXmlTopic, sxAvroEnabled, sxAvroTopic,
                        sxKafkaEnabled, "direct:publish.sx.avro.kafka"));
            }
        } catch (RuntimeException e) {
            // Spring does not call @PreDestroy when init fails, so release the connections made so far here
            sinks().forEach(TopicSink::close);
            routes.clear();
            throw e;
        }
        if (!routes.isEmpty() && executor == null) {
            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize);
            metricsService.gauge(QUEUE_GAUGE_NAME, queue, BlockingQueue::size);
            executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, queue, namedThreads(),
                    (task, pool) -> rejected(task));
        }
    }

    private void addRoute(TypeRoute route) {
        if (route != null) {
            routes.put(route.dataType(), route);
            logger.info("Publishing {} directly to Pub/Sub: xml, {}", route.dataType(), route.avroSink() != null ? "avro" : "no avro");
        }
    }

    /** @return null when the XML topic is not a Pub/Sub topic, which leaves the data type to the Camel route */
    private TypeRoute route(SiriDataType dataType, Converter converter, String xmlTopic, boolean avroEnabled, String avroTopic,
                            boolean kafkaEnabled, String kafkaEndpoint) {
        Optional<TopicSink> xmlSink = sinkFactory.create(xmlTopic);
        if (xmlSink.isEmpty()) {
            logger.warn("{} topic '{}' is not a Pub/Sub topic: {} stays on the Camel topic route", dataType, xmlTopic, dataType);
            return null;
        }
        TopicSink avroSink;
        try {
            avroSink = avroEnabled ? sinkFactory.create(avroTopic).orElse(null) : null;
        } catch (RuntimeException e) {
            xmlSink.get().close();
            throw e;
        }
        return new TypeRoute(dataType, converter, xmlSink.get(), avroSink, avroEnabled, kafkaEnabled, kafkaEndpoint);
    }

    private List<TopicSink> sinks() {
        List<TopicSink> sinks = new ArrayList<>();
        for (TypeRoute route : routes.values()) {
            sinks.add(route.xmlSink());
            if (route.avroSink() != null) {
                sinks.add(route.avroSink());
            }
        }
        return sinks;
    }

    /** Whether this publisher publishes the data type to the topics, instead of the Camel topic route */
    public boolean handles(SiriDataType dataType) {
        return routes.containsKey(dataType);
    }

    /**
     * Publishes elements that were just stored. Returns at once; the work runs on the publisher's own threads.
     * Data types this publisher does not handle are ignored.
     */
    public void publishAsync(SiriDataType dataType, List<?> addedOrUpdated, String codespace) {
        TypeRoute route = routes.get(dataType);
        if (route == null || executor == null || addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        long storedAt = System.currentTimeMillis();
        String breadcrumbId = MDC.get(BREADCRUMB_MDC_KEY);
        Runnable work = () -> {
            MDC.put(BREADCRUMB_MDC_KEY, breadcrumbId);
            try {
                publish(route, delivery(dataType, addedOrUpdated), codespace, breadcrumbId, storedAt);
            } catch (RuntimeException e) {
                logger.warn("Unable to publish {} {} updates for {} to the outbound topics", addedOrUpdated.size(), dataType, codespace, e);
                countDropped(route, addedOrUpdated.size());
            } finally {
                MDC.remove(BREADCRUMB_MDC_KEY);
            }
        };
        PublishTask task = new PublishTask(route, addedOrUpdated.size(), codespace, work);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // Executors without a rejection handler, e.g. while shutting down
            rejected(task);
        }
    }

    @SuppressWarnings("unchecked")
    private Siri delivery(SiriDataType dataType, List<?> addedOrUpdated) {
        return dataType == SiriDataType.ESTIMATED_TIMETABLE
                ? siriObjectFactory.createETServiceDelivery((List<EstimatedVehicleJourney>) addedOrUpdated)
                : siriObjectFactory.createSXServiceDelivery((List<PtSituationElement>) addedOrUpdated);
    }

    /** Never thrown back to SiriHandler: the elements are counted as failed for every topic they were meant for */
    private void rejected(Runnable runnable) {
        if (!(runnable instanceof PublishTask task)) {
            return;
        }
        countDropped(task.route(), task.elements());
        long rejected = rejectedDeliveries.incrementAndGet();
        if (rejected == 1 || rejected % 1000 == 0) {
            logger.warn("Publisher queue full or shut down: dropped {} {} updates for {} ({} deliveries dropped so far)",
                    task.elements(), task.route().dataType(), task.codespace(), rejected);
        }
    }

    private void countDropped(TypeRoute route, long elements) {
        metricsService.markPublishFailed(route.dataType(), XML, elements);
        if (route.avroSink() != null) {
            metricsService.markPublishFailed(route.dataType(), AVRO, elements);
        }
        if (route.kafkaEnabled()) {
            metricsService.markPublishFailed(route.dataType(), KAFKA, elements);
        }
    }

    private void publish(TypeRoute route, Siri delivery, String codespace, String breadcrumbId, long storedAt) {
        SiriDataType dataType = route.dataType();
        // A copy: the stored elements are also pushed to outbound subscriptions, and must not change here. Copied
        // here rather than in transform(), which publishes the original, unmapped, when the copy fails
        Siri copy = SiriObjectFactory.deepCopy(delivery);
        Siri mapped = SiriValueTransformer.transform(copy,
                mappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT), false, false);
        metricsService.recordOutboundStage(dataType, PrometheusMetricsService.OUTBOUND_STAGE_TRANSFORMED, storedAt);

        for (ElementMessages messages : route.converter().convert(mapped, codespace, breadcrumbId, storedAt)) {
            String elementId = messages.elementId();
            metricsService.recordOutboundStage(dataType, PrometheusMetricsService.OUTBOUND_STAGE_HANDED_TO_CLIENT, storedAt);
            if (messages.xml() != null) {
                send(route.xmlSink(), messages.xml(), dataType, XML, storedAt, elementId);
            } else {
                onFailure(dataType, XML, elementId, messages.xmlError());
            }
            if (messages.avro() != null) {
                publishAvro(route, messages.avro(), storedAt, elementId);
            } else {
                if (route.avroSink() != null) {
                    onFailure(dataType, AVRO, elementId, messages.avroError());
                }
                if (route.kafkaEnabled()) {
                    onFailure(dataType, KAFKA, elementId, messages.avroError());
                }
            }
        }
    }

    private void publishAvro(TypeRoute route, ElementMessages.Avro avro, long storedAt, String elementId) {
        SiriDataType dataType = route.dataType();
        if (route.avroSink() != null) {
            send(route.avroSink(), avro.json(), dataType, AVRO, storedAt, elementId);
        } else if (route.avroEnabled()) {
            // The Avro topic is mock:ignore; the Camel Avro route counted these records all the same
            metricsService.registerAvroPubsubRecord(dataType);
        }
        try {
            kafkaProducer.asyncRequestBodyAndHeaders(route.kafkaEndpoint(), avro.avroRecord(), avro.kafkaHeaders())
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            kafkaFailed(route, elementId, error);
                        }
                    });
        } catch (RuntimeException e) {
            // E.g. Camel already stopped while the queue drains at shutdown
            kafkaFailed(route, elementId, e);
        }
    }

    private void kafkaFailed(TypeRoute route, String elementId, Throwable error) {
        if (route.kafkaEnabled()) {
            onFailure(route.dataType(), KAFKA, elementId, error);
        }
    }

    private void send(TopicSink sink, PubsubMessage message, SiriDataType dataType, String format, long storedAt, String elementId) {
        long started = System.nanoTime();
        ApiFuture<String> future;
        try {
            future = sink.publish(message);
        } catch (RuntimeException e) {
            onFailure(dataType, format, elementId, e);
            return;
        }
        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onSuccess(String messageId) {
                metricsService.recordPublish(dataType, format, Duration.ofNanos(System.nanoTime() - started));
                if (AVRO.equals(format)) {
                    metricsService.recordOutboundLatency(dataType, storedAt);
                    metricsService.registerAvroPubsubRecord(dataType);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                SiriTopicPublisher.this.onFailure(dataType, format, elementId, t);
            }
        }, MoreExecutors.directExecutor());
    }

    private void onFailure(SiriDataType dataType, String format, String elementId, Throwable t) {
        metricsService.markPublishFailed(dataType, format);
        if (logger.isWarnEnabled()) {
            logger.warn("Unable to publish {} {} to the {} topic: {}", dataType, elementId, format, String.valueOf(t));
        }
    }

    /**
     * Drains the queue, then sends what the clients still hold, within about 25 s in all, so a pod stop fits its
     * grace period. Updates still queued after the drain timeout, or arriving later, are dropped and counted.
     * Kafka records of deliveries drained after Camel has stopped are lost (counted as kafka failures).
     */
    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(drainTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    List<Runnable> dropped = executor.shutdownNow();
                    logger.warn("Publisher threads still busy after {} ms; dropping {} queued deliveries",
                            drainTimeoutMillis, dropped.size());
                    dropped.forEach(this::rejected);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Kept, shut down, so later updates are rejected and counted
        }
        List<TopicSink> sinks = sinks();
        // All at once, then waited for against one deadline
        sinks.forEach(TopicSink::startShutdown);
        long deadline = System.nanoTime() + SINKS_SHUTDOWN_TIMEOUT.toNanos();
        for (TopicSink sink : sinks) {
            sink.awaitShutdown(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        }
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "topic-publisher-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
