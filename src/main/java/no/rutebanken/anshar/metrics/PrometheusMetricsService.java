/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.metrics;

import com.hazelcast.replicatedmap.ReplicatedMap;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.PreDestroy;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.MappingNames;
import no.rutebanken.anshar.routes.validation.ValidationType;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static no.rutebanken.anshar.subscription.SubscriptionSetup.SubscriptionMode.AVRO_PUBSUB;

@Component
public class PrometheusMetricsService extends PrometheusMeterRegistry {

    private static final String DATATYPE_TAG_NAME = "dataType";
    private static final String AGENCY_TAG_NAME = "agency";
    private static final String MAPPING_ID_TAG = "mappingId";
    private static final String MAPPING_NAME_TAG = "mappingName";
    private static final String SIRI_CONTENT_NAME_TAG = "siriContent";
    private static final String SIRI_CONTENT_LABEL_TAG = "siriContentLabel";
    private static final String SIRI_CONTENT_GROUP_TAG = "group";
    private static final String SERVICE_JOURNEY_ID_TAG_NAME = "serviceJourney";

    private static final String KAFKA_STATUS_TAG = "kafkaStatus";
    private static final String KAFKA_TOPIC_NAME = "kafkaTopic";

    private static final String CODESPACE_TAG_NAME = "codespace";
    private static final String VALIDATION_TYPE_TAG_NAME = "validationType";
    private static final String VALIDATION_RULE_TAG_NAME = "category";
    private static final String SCHEMA_VALID_TAG_NAME = "schema";
    private static final String PROFILE_VALID_TAG_NAME = "profile";

    @Autowired
    protected SubscriptionManager manager;

    private static final String METRICS_PREFIX = "app.anshar.";
    private static final String DATA_COUNTER_NAME = METRICS_PREFIX + "data.counter";
    private static final String DATA_TOTAL_COUNTER_NAME = METRICS_PREFIX + "data.total";
    private static final String DATA_SUCCESS_COUNTER_NAME = METRICS_PREFIX + "data.success";
    private static final String DATA_EXPIRED_COUNTER_NAME = METRICS_PREFIX + "data.expired";
    private static final String DATA_IGNORED_COUNTER_NAME = METRICS_PREFIX + "data.ignored";
    private static final String DATA_OUTBOUND_COUNTER_NAME = METRICS_PREFIX + "data.outbound";
    private static final String SUBSCRIPTION_OUTBOUND_COUNTER_NAME = METRICS_PREFIX + "subscription.outbound";
    private static final String SUBSCRIPTION_OUTBOUND_CONCURRENT_REQUESTS = METRICS_PREFIX + "concurrent.outbound.requests";
    private static final String SUBSCRIPTION_OUTBOUND_QUEUE = METRICS_PREFIX + "concurrent.outbound.queue";

    private static final String DATA_MAPPING_COUNTER_NAME = METRICS_PREFIX + "data.mapping";

    private static final String SIRI_CONTENT_COUNTER_NAME = METRICS_PREFIX + "siri.content";

    private static final String KAFKA_COUNTER_NAME = METRICS_PREFIX + "data.kafka";

    private static final String DATA_VALIDATION_COUNTER = METRICS_PREFIX + "data.validation";
    private static final String DATA_VALIDATION_RESULT_COUNTER = METRICS_PREFIX + "data.validation.result";

    @Value("${anshar.metrics.include.failing.subscriptions:false}")
    private boolean includeSubscriptionFailingMetrics;
    private Map<String, ExecutorService> outboundThreadFactoryMap;

    public PrometheusMetricsService() {
        super(PrometheusConfig.DEFAULT);
    }

    /** Exchange header, then property, carrying when outbound updates were stored, in epoch millis */
    public static final String STORED_AT_HEADER = "AnsharStoredAtMillis";

    private static final String DATA_DROPPED_COUNTER_NAME = METRICS_PREFIX + "data.dropped";
    private static final String LATENCY_QUEUE_NAME = METRICS_PREFIX + "latency.queue";
    private static final String LATENCY_SOURCE_NAME = METRICS_PREFIX + "latency.source";
    private static final String LATENCY_SOURCE_MISSING_NAME = METRICS_PREFIX + "latency.source.missing";
    private static final String LATENCY_OUTBOUND_NAME = METRICS_PREFIX + "latency.outbound";
    private static final String LATENCY_OUTBOUND_STAGE_NAME = METRICS_PREFIX + "latency.outbound.stage";
    private static final String STAGE_TAG_NAME = "stage";

    /** Outbound stage: the delivery entered the topic route, after the push pool and async producer */
    public static final String OUTBOUND_STAGE_ROUTE_START = "route_start";
    /** Outbound stage: outbound ID mapping and value adapters applied to the delivery */
    public static final String OUTBOUND_STAGE_TRANSFORMED = "transformed";
    /** Outbound stage: the delivery converted to XML and split by split.xsl, before the split starts */
    public static final String OUTBOUND_STAGE_SPLIT_READY = "split_ready";
    /** Outbound stage: a split element starts publishing, after waiting for a split thread */
    public static final String OUTBOUND_STAGE_ELEMENT_START = "element_start";

    // Internal steps: Pub/Sub hops and processing, expected in milliseconds
    private static final Duration[] INTERNAL_LATENCY_BUCKETS = {
            Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(2500),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60)};

    // Operator reporting until stored: seconds to minutes
    private static final Duration[] SOURCE_LATENCY_BUCKETS = {
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15), Duration.ofSeconds(30),
            Duration.ofSeconds(60), Duration.ofSeconds(120), Duration.ofSeconds(300), Duration.ofSeconds(600)};

    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    private Timer latencyTimer(String name, String description, Duration[] buckets, String... tags) {
        return latencyTimers.computeIfAbsent(name + String.join(",", tags), key ->
                Timer.builder(name)
                        .description(description)
                        .tags(tags)
                        .serviceLevelObjectives(buckets)
                        .register(this));
    }

    /**
     * Counts elements that were received but not stored, by reason: invalid_structure, not_updated,
     * outdated, duplicate, expired, no_valid_until and no_datasource.
     */
    public void markDataDropped(SiriDataType dataType, String codespace, String reason, long count) {
        if (count > 0) {
            counter(DATA_DROPPED_COUNTER_NAME,
                    DATATYPE_TAG_NAME, dataType.name(), CODESPACE_TAG_NAME, codespace, "reason", reason
            ).increment(count);
        }
    }

    /**
     * Counts elements dropped before they reach a repository, e.g. when no codespace can be
     * resolved. They count as received and ignored, so data.total still adds up, and in
     * data.dropped with the reason.
     */
    public void markDroppedBeforeStorage(SiriDataType dataType, String datasetId, String reason, long count) {
        if (count > 0) {
            registerIncomingData(dataType, datasetId, count, 0, 0, count);
            markDataDropped(dataType, datasetId, reason, count);
        }
    }

    /** Time from a message being published to Anshar's internal queue until this pod consumed it */
    public void recordQueueLatency(SiriDataType dataType, com.google.protobuf.Timestamp publishTime) {
        Instant published = Instant.ofEpochSecond(publishTime.getSeconds(), publishTime.getNanos());
        latencyTimer(LATENCY_QUEUE_NAME,
                "Time from publishing to Anshar's internal queue until a data pod consumed the message",
                INTERNAL_LATENCY_BUCKETS, DATATYPE_TAG_NAME, dataType.name()
        ).record(Duration.between(published, Instant.now()));
    }

    /** Time from the element's reported time until Anshar stored it */
    public void recordSourceLatency(SiriDataType dataType, String codespace, Duration latency) {
        latencyTimer(LATENCY_SOURCE_NAME,
                "Time from the reported time in the data until Anshar stored it",
                SOURCE_LATENCY_BUCKETS, DATATYPE_TAG_NAME, dataType.name(), CODESPACE_TAG_NAME, codespace
        ).record(latency);
    }

    /** Counts stored elements without a reported time, so they cannot be timed */
    public void markSourceTimeMissing(SiriDataType dataType, String codespace, long count) {
        if (count > 0) {
            counter(LATENCY_SOURCE_MISSING_NAME, DATATYPE_TAG_NAME, dataType.name(), CODESPACE_TAG_NAME, codespace).increment(count);
        }
    }

    /**
     * Records the time since the updates were stored, read from the {@link #STORED_AT_HEADER}
     * exchange property. Use after publishing to the outbound topic.
     */
    public Processor outboundLatencyRecorder(SiriDataType dataType) {
        return exchange -> {
            Long storedAt = exchange.getProperty(STORED_AT_HEADER, Long.class);
            if (storedAt != null) {
                latencyTimer(LATENCY_OUTBOUND_NAME,
                        "Time from Anshar storing updates until they were published to the outbound topic",
                        INTERNAL_LATENCY_BUCKETS, DATATYPE_TAG_NAME, dataType.name()
                ).record(Duration.ofMillis(System.currentTimeMillis() - storedAt));
            }
        };
    }

    /**
     * Records the time since the updates were stored, read from the {@link #STORED_AT_HEADER}
     * exchange property, at an intermediate step of the outbound topic route. Together with
     * {@link #outboundLatencyRecorder} this shows where outbound latency is spent.
     */
    public Processor outboundStageRecorder(SiriDataType dataType, String stage) {
        return exchange -> {
            Long storedAt = exchange.getProperty(STORED_AT_HEADER, Long.class);
            if (storedAt != null) {
                latencyTimer(LATENCY_OUTBOUND_STAGE_NAME,
                        "Time from Anshar storing updates until they reached a step of the outbound topic route",
                        INTERNAL_LATENCY_BUCKETS, DATATYPE_TAG_NAME, dataType.name(), STAGE_TAG_NAME, stage
                ).record(Duration.ofMillis(System.currentTimeMillis() - storedAt));
            }
        };
    }

    @PreDestroy
    public void shutdown() {
        this.close();
    }

    public void registerIncomingData(SiriDataType dataType, String agencyId, long total, long updated, long expired, long ignored) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));

        counter(DATA_TOTAL_COUNTER_NAME,   counterTags).increment(total);
        counter(DATA_SUCCESS_COUNTER_NAME, counterTags).increment(updated);
        counter(DATA_EXPIRED_COUNTER_NAME, counterTags).increment(expired);
        counter(DATA_IGNORED_COUNTER_NAME, counterTags).increment(ignored);
    }

    public void registerDataMapping(SiriDataType dataType, String agencyId, MappingNames mappingName, int mappedCount) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));
        counterTags.add(new ImmutableTag(MAPPING_NAME_TAG, mappingName.toString()));
        counterTags.add(new ImmutableTag(MAPPING_ID_TAG, mappingName.name()));

        counter(DATA_MAPPING_COUNTER_NAME, counterTags).increment(mappedCount);
    }

    public void registerSiriContent(SiriDataType dataType, String agencyId, String serviceJourneyId, SiriContent content) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        if (agencyId != null) {
            counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));
        }
        if (serviceJourneyId != null) {
            counterTags.add(new ImmutableTag(SERVICE_JOURNEY_ID_TAG_NAME, serviceJourneyId));
        }
        counterTags.add(new ImmutableTag(SIRI_CONTENT_NAME_TAG, content.name()));
        counterTags.add(new ImmutableTag(SIRI_CONTENT_LABEL_TAG, content.getLabel()));
        counterTags.add(new ImmutableTag(SIRI_CONTENT_GROUP_TAG, content.getGroup().name()));

        counter(SIRI_CONTENT_COUNTER_NAME, counterTags).increment();
    }

    public void registerAckedKafkaRecord(String topic) {
        registerKafkaRecord(topic, KafkaStatus.ACKED);
    }
    public void registerKafkaRecord(String topic, KafkaStatus status) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(KAFKA_TOPIC_NAME, topic));
        counterTags.add(new ImmutableTag(KAFKA_STATUS_TAG, status.name()));

        counter(KAFKA_COUNTER_NAME, counterTags).increment();
    }

    public void registerAvroPubsubRecord(SiriDataType dataType) {
        countOutgoingData(dataType, AVRO_PUBSUB, 1);
    }

    public void countOutgoingData(Siri siri, SubscriptionSetup.SubscriptionMode mode) {
        SiriDataType dataType = null;
        int count = 0;
        if (siri != null && siri.getServiceDelivery() != null) {
            if (siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null &&
                    !siri.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
                EstimatedTimetableDeliveryStructure timetableDeliveryStructure = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0);
                if (timetableDeliveryStructure != null && timetableDeliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        !timetableDeliveryStructure.getEstimatedJourneyVersionFrames().isEmpty()) {
                    EstimatedVersionFrameStructure estimatedVersionFrameStructure = timetableDeliveryStructure.getEstimatedJourneyVersionFrames().get(0);
                    if (estimatedVersionFrameStructure != null &&  estimatedVersionFrameStructure.getEstimatedVehicleJourneies() != null) {

                        dataType = SiriDataType.ESTIMATED_TIMETABLE;
                        count = estimatedVersionFrameStructure.getEstimatedVehicleJourneies().size();
                    }
                }
            } else if (siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null &&
                        !siri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
                VehicleMonitoringDeliveryStructure deliveryStructure = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0);
                if (deliveryStructure != null) {
                    dataType = SiriDataType.VEHICLE_MONITORING;
                    count = deliveryStructure.getVehicleActivities().size();
                }
            } else if (siri.getServiceDelivery().getSituationExchangeDeliveries() != null &&
                        !siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
                SituationExchangeDeliveryStructure deliveryStructure = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0);
                if (deliveryStructure != null && deliveryStructure.getSituations() != null) {
                    dataType = SiriDataType.SITUATION_EXCHANGE;
                    count = deliveryStructure.getSituations().getPtSituationElements().size();
                }
            }
            countOutgoingData(dataType, mode, count);
        }

    }

    public void addValidationMetrics(
        SiriDataType dataType, String codespaceId, ValidationType validationType, String message, Integer count
    ) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(CODESPACE_TAG_NAME, codespaceId));
        counterTags.add(new ImmutableTag(VALIDATION_TYPE_TAG_NAME, validationType.name()));
        counterTags.add(new ImmutableTag(VALIDATION_RULE_TAG_NAME, message));

        counter(DATA_VALIDATION_COUNTER, counterTags).increment(count);
    }

    public void addValidationResult(
        SiriDataType dataType, String codespaceId, boolean schemaValid, boolean profileValid
    ) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(CODESPACE_TAG_NAME, codespaceId));
        counterTags.add(new ImmutableTag(SCHEMA_VALID_TAG_NAME, ""+schemaValid));
        counterTags.add(new ImmutableTag(PROFILE_VALID_TAG_NAME, ""+profileValid));

        counter(DATA_VALIDATION_RESULT_COUNTER, counterTags).increment();
    }

    private void countOutgoingData(SiriDataType dataType, SubscriptionSetup.SubscriptionMode mode, long objectCount) {
        if (dataType != null && objectCount > 0) {
            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
            counterTags.add(new ImmutableTag("mode", mode.name()));

            counter(DATA_OUTBOUND_COUNTER_NAME, counterTags).increment(objectCount);
        }
    }


    public void markPostToSubscription(SiriDataType dataType, SubscriptionSetup.SubscriptionMode mode, String subscriptionId, int statusCode) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag("mode", mode.name()));
        counterTags.add(new ImmutableTag("subscriptionId", subscriptionId));
        counterTags.add(new ImmutableTag("statusCode", ""+statusCode));

        counter(SUBSCRIPTION_OUTBOUND_COUNTER_NAME, counterTags).increment(1);
    }

    final Map<String, Integer> gaugeValues = new ConcurrentHashMap<>();

    private String gaugeDataset(SiriDataType subscriptionType, String agencyId, Integer count) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, subscriptionType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));

        String key = "" + subscriptionType + agencyId;
        gaugeValues.put(key, count);

        gauge(DATA_COUNTER_NAME, counterTags, key, value -> gaugeValues.getOrDefault(key, 0));
        return key;
    }

    @Override
    public synchronized String scrape() {
        update();
        return super.scrape();
    }

    public synchronized void update() {

        for (Meter meter : getMeters()) {
            if (DATA_COUNTER_NAME.equals(meter.getId().getName())) {
                this.remove(meter);
            }
            if (SUBSCRIPTION_OUTBOUND_CONCURRENT_REQUESTS.equals(meter.getId().getName())) {
                this.remove(meter);
            }
            if (SUBSCRIPTION_OUTBOUND_QUEUE.equals(meter.getId().getName())) {
                this.remove(meter);
            }
            if (SUBSCRIPTION_OUTBOUND_COUNTER_NAME.equals(meter.getId().getName())) {
                // Counters are tagged with subscriptionId - remove when the outbound subscription no longer exists
                String subscriptionId = meter.getId().getTag("subscriptionId");
                if (subscriptionId != null && !outboundThreadFactoryMap.containsKey(subscriptionId)) {
                    this.remove(meter);
                }
            }
        }
        Set<String> currentGaugeKeys = new HashSet<>();

        EstimatedTimetables estimatedTimetables = ApplicationContextHolder.getContext().getBean(EstimatedTimetables.class);
        Map<String, Integer> datasetSize = estimatedTimetables.getLocalDatasetSize();
        for (Map.Entry<String, Integer> entry : datasetSize.entrySet()) {
            currentGaugeKeys.add(gaugeDataset(SiriDataType.ESTIMATED_TIMETABLE, entry.getKey(), entry.getValue()));
        }

        Situations situations = ApplicationContextHolder.getContext().getBean(Situations.class);
        datasetSize = situations.getLocalDatasetSize();
        for (Map.Entry<String, Integer> entry : datasetSize.entrySet()) {
            currentGaugeKeys.add(gaugeDataset(SiriDataType.SITUATION_EXCHANGE, entry.getKey(), entry.getValue()));
        }

        VehicleActivities vehicleActivities = ApplicationContextHolder.getContext().getBean(VehicleActivities.class);
        datasetSize = vehicleActivities.getLocalDatasetSize();
        for (Map.Entry<String, Integer> entry : datasetSize.entrySet()) {
            currentGaugeKeys.add(gaugeDataset(SiriDataType.VEHICLE_MONITORING, entry.getKey(), entry.getValue()));
        }
        // Values are only removed for datasets that no longer exist
        gaugeValues.keySet().retainAll(currentGaugeKeys);

        for (Map.Entry<String, ExecutorService> entry : outboundThreadFactoryMap.entrySet()) {
            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag("subscriptionId", entry.getKey()));
            ThreadPoolExecutor executor = (ThreadPoolExecutor) entry.getValue();
            gauge(SUBSCRIPTION_OUTBOUND_CONCURRENT_REQUESTS, counterTags, executor.getActiveCount());
            gauge(SUBSCRIPTION_OUTBOUND_QUEUE, counterTags, executor.getQueue().size());
        }

        if (includeSubscriptionFailingMetrics) {
            ReplicatedMap<String, SubscriptionSetup> subscriptions = manager.subscriptions;
            for (SubscriptionSetup subscription : subscriptions.values()) {

                SiriDataType subscriptionType = subscription.getSubscriptionType();

                String gauge_baseName = METRICS_PREFIX + "subscription";

                String gauge_failing = gauge_baseName + ".failing";
                String gauge_data_failing = gauge_baseName + ".data_failing";


                List<Tag> counterTags = new ArrayList<>();
                counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, subscriptionType.name()));
                counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, subscription.getDatasetId()));
                counterTags.add(new ImmutableTag("vendor", subscription.getVendor()));


                //Flag as failing when ACTIVE, and NOT HEALTHY
                gauge(gauge_failing, getTagsWithTimeLimit(counterTags, "now"), subscription.getSubscriptionId(), value ->
                        (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                                !manager.isSubscriptionHealthy(subscription.getSubscriptionId())) ? 1 : 0);

                //Set flag as data failing when ACTIVE, and NOT receiving data

                gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "5min"), subscription.getSubscriptionId(), value ->
                        isSubscriptionFailing(manager, subscription, 5 * 60));

                gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "15min"), subscription.getSubscriptionId(), value ->
                        isSubscriptionFailing(manager, subscription, 15 * 60));

                gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "30min"), subscription.getSubscriptionId(), value ->
                        isSubscriptionFailing(manager, subscription, 30 * 60));
            }
        }
    }

    private List<Tag> getTagsWithTimeLimit(List<Tag> counterTags, String timeLimit) {
        List<Tag> counterTagsClone = new ArrayList<>(counterTags);
        counterTagsClone.add(new ImmutableTag("timelimit", timeLimit));
        return counterTagsClone;
    }

    private double isSubscriptionFailing(SubscriptionManager manager, SubscriptionSetup subscription, int allowedSeconds) {
        if (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                !manager.isSubscriptionReceivingData(subscription.getSubscriptionId(), allowedSeconds)) {
            return 1;
        }
        return 0;
    }

    public void registerOutboundThreadFactoryMap(Map<String, ExecutorService> threadFactoryMap) {
        this.outboundThreadFactoryMap = threadFactoryMap;
    }
}
