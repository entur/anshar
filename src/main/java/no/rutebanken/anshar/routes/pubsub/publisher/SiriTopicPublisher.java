package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.pubsub.publisher.SiriTopicMessages.Converted;
import no.rutebanken.anshar.routes.pubsub.publisher.SiriTopicMessages.Failed;
import no.rutebanken.anshar.routes.pubsub.publisher.SiriTopicMessages.Result;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes stored ET journeys and SX situations to the outbound XML and Avro-JSON Pub/Sub topics, one message per
 * element, without waiting for Pub/Sub to answer. Replaces the Camel topic routes for ET and SX when
 * {@code anshar.outbound.pubsub.direct.enabled} is on; the Kafka publish still goes through its Camel route.
 */
@Service
public class SiriTopicPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SiriTopicPublisher.class);

    static final String XML = "xml";
    static final String AVRO = "avro";
    static final String KAFKA = "kafka";
    private static final String BREADCRUMB_MDC_KEY = "camel.breadcrumbId";
    private static final String QUEUE_GAUGE_NAME = "app.anshar.outbound.publisher.queue";
    private static final Duration EXECUTOR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SINKS_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    /** The work for one delivery, which knows what to count when it cannot run */
    private record PublishTask(SiriDataType dataType, int elements, String codespace, Runnable work) implements Runnable {
        @Override
        public void run() {
            work.run();
        }
    }

    /** Builds the messages for a mapped delivery; see {@link SiriTopicMessages} */
    @FunctionalInterface
    interface Converter {
        List<Result> convert(Siri mappedDelivery, String codespace, String breadcrumbId, long storedAt);
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

    @Autowired
    private TopicSinkFactory sinkFactory;
    @Autowired
    private PrometheusMetricsService metricsService;
    @Autowired
    private SiriObjectFactory siriObjectFactory;
    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;
    @Produce
    private ProducerTemplate kafkaProducer;

    private ExecutorService executor;
    private final AtomicLong rejectedDeliveries = new AtomicLong();
    private Converter etConverter = SiriTopicMessages::forEstimatedTimetables;
    private Converter sxConverter = SiriTopicMessages::forSituations;

    private Optional<TopicSink> etXmlSink = Optional.empty();
    private Optional<TopicSink> etAvroSink = Optional.empty();
    private Optional<TopicSink> sxXmlSink = Optional.empty();
    private Optional<TopicSink> sxAvroSink = Optional.empty();

    @PostConstruct
    void init() {
        if (!directEnabled || !pushToTopicEnabled) {
            return;
        }
        if (etTopicEnabled) {
            etXmlSink = sinkFactory.create(etXmlTopic);
            etAvroSink = etAvroEnabled ? sinkFactory.create(etAvroTopic) : Optional.empty();
        }
        if (sxTopicEnabled) {
            sxXmlSink = sinkFactory.create(sxXmlTopic);
            sxAvroSink = sxAvroEnabled ? sinkFactory.create(sxAvroTopic) : Optional.empty();
        }
        if (executor == null) {
            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize);
            metricsService.gauge(QUEUE_GAUGE_NAME, queue, BlockingQueue::size);
            executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, queue, namedThreads(),
                    (task, pool) -> rejected(task));
        }
        logger.info("Publishing ET and SX directly to Pub/Sub: ET xml={}, avro={}; SX xml={}, avro={}",
                etXmlSink.isPresent(), etAvroSink.isPresent(), sxXmlSink.isPresent(), sxAvroSink.isPresent());
    }

    /** Whether ET and SX go through this publisher instead of the Camel topic routes */
    public boolean isEnabled() {
        return directEnabled;
    }

    /**
     * Publishes elements that were just stored. Returns at once; the work runs on the publisher's own threads.
     * Data types other than ET and SX are ignored.
     */
    public void publishAsync(SiriDataType dataType, List<?> addedOrUpdated, String codespace) {
        if (!directEnabled || executor == null || addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        boolean et = dataType == SiriDataType.ESTIMATED_TIMETABLE && etTopicEnabled;
        boolean sx = dataType == SiriDataType.SITUATION_EXCHANGE && sxTopicEnabled;
        if (!et && !sx) {
            return;
        }
        long storedAt = System.currentTimeMillis();
        String breadcrumbId = MDC.get(BREADCRUMB_MDC_KEY);
        Runnable work = () -> {
            MDC.put(BREADCRUMB_MDC_KEY, breadcrumbId);
            try {
                if (et) {
                    @SuppressWarnings("unchecked")
                    List<EstimatedVehicleJourney> journeys = (List<EstimatedVehicleJourney>) addedOrUpdated;
                    publish(dataType, siriObjectFactory.createETServiceDelivery(journeys), codespace, breadcrumbId, storedAt,
                            etConverter, etXmlSink, etAvroSink, "direct:publish.et.avro.kafka");
                } else {
                    @SuppressWarnings("unchecked")
                    List<PtSituationElement> situations = (List<PtSituationElement>) addedOrUpdated;
                    publish(dataType, siriObjectFactory.createSXServiceDelivery(situations), codespace, breadcrumbId, storedAt,
                            sxConverter, sxXmlSink, sxAvroSink, "direct:publish.sx.avro.kafka");
                }
            } catch (RuntimeException e) {
                logger.warn("Unable to publish {} {} updates for {} to the outbound topics", addedOrUpdated.size(), dataType, codespace, e);
            } finally {
                MDC.remove(BREADCRUMB_MDC_KEY);
            }
        };
        PublishTask task = new PublishTask(dataType, addedOrUpdated.size(), codespace, work);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // Executors without a rejection handler, e.g. while shutting down
            rejected(task);
        }
    }

    /** Never thrown back to SiriHandler: the elements are counted as failed for both topics */
    private void rejected(Runnable runnable) {
        if (!(runnable instanceof PublishTask task)) {
            return;
        }
        metricsService.markPublishFailed(task.dataType(), XML, task.elements());
        metricsService.markPublishFailed(task.dataType(), AVRO, task.elements());
        long rejected = rejectedDeliveries.incrementAndGet();
        if (rejected == 1 || rejected % 1000 == 0) {
            logger.warn("Publisher queue full or shut down: dropped {} {} updates for {} ({} deliveries dropped so far)",
                    task.elements(), task.dataType(), task.codespace(), rejected);
        }
    }

    private void publish(SiriDataType dataType, Siri delivery, String codespace, String breadcrumbId, long storedAt,
                         Converter converter, Optional<TopicSink> xmlSink, Optional<TopicSink> avroSink, String kafkaEndpoint) {
        // A copy: the stored elements are also pushed to outbound subscriptions, and must not change here
        Siri mapped = SiriValueTransformer.transform(delivery,
                mappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT), true, false);
        metricsService.recordOutboundStage(dataType, PrometheusMetricsService.OUTBOUND_STAGE_TRANSFORMED, storedAt);

        for (Result result : converter.convert(mapped, codespace, breadcrumbId, storedAt)) {
            if (result instanceof Failed failed) {
                logger.warn("Unable to build topic messages for {} {}", dataType, failed.elementId(), failed.error());
                metricsService.markPublishFailed(dataType, XML);
                metricsService.markPublishFailed(dataType, AVRO);
                continue;
            }
            ElementMessages messages = ((Converted) result).messages();
            metricsService.recordOutboundStage(dataType, PrometheusMetricsService.OUTBOUND_STAGE_HANDED_TO_CLIENT, storedAt);
            xmlSink.ifPresent(sink -> send(sink, messages.xml(), dataType, XML, storedAt, messages.elementId()));
            avroSink.ifPresent(sink -> send(sink, messages.avroJson(), dataType, AVRO, storedAt, messages.elementId()));
            try {
                kafkaProducer.asyncRequestBodyAndHeaders(kafkaEndpoint, messages.avroRecord(), messages.kafkaHeaders());
            } catch (RuntimeException e) {
                // E.g. Camel already stopped while the queue drains at shutdown
                onFailure(dataType, KAFKA, messages.elementId(), e);
            }
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
        logger.warn("Unable to publish {} {} to the {} topic: {}", dataType, elementId, format, t.toString());
    }

    /**
     * Drains the queue, then sends what the clients still hold, within about 25 s in all, so a pod stop fits its
     * grace period. Kafka records of deliveries drained after Camel has stopped are lost (counted as kafka failures).
     */
    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    logger.warn("Publisher threads still busy after {} s; queued updates are dropped",
                            EXECUTOR_SHUTDOWN_TIMEOUT.toSeconds());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        List<TopicSink> sinks = List.of(etXmlSink, etAvroSink, sxXmlSink, sxAvroSink).stream()
                .flatMap(Optional::stream).toList();
        // All at once, then waited for against one deadline
        sinks.forEach(TopicSink::startShutdown);
        long deadline = System.nanoTime() + SINKS_SHUTDOWN_TIMEOUT.toNanos();
        for (TopicSink sink : sinks) {
            sink.awaitShutdown(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        }
        etXmlSink = etAvroSink = sxXmlSink = sxAvroSink = Optional.empty();
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
