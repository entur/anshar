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

package no.rutebanken.anshar.routes.outbound;

import com.google.common.base.Suppliers;
import com.hazelcast.map.IMap;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.pubsub.publisher.SiriTopicPublisher;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.entur.siri.validator.SiriValidator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.AbstractSubscriptionStructure;
import uk.org.siri.siri21.CheckStatusRequestStructure;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.FacilityMonitoringSubscriptionStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri21.SubscriptionRequest;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringSubscriptionStructure;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MILLIS;
import static no.rutebanken.anshar.routes.kafka.KafkaConfig.CODESPACE_ID_KAFKA_HEADER_NAME;
import static no.rutebanken.anshar.routes.outbound.SiriHelper.resolveSiriVersionStr;

@SuppressWarnings("unchecked")
@Service
@Configuration
@DependsOn("camelRouteManager")
public class ServerSubscriptionManager {

    private final Logger logger = LoggerFactory.getLogger(ServerSubscriptionManager.class);

    private static final int BATCH_FLUSH_THREADS = 4;

    @Autowired
    IMap<String, OutboundSubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getFailTrackerMap")
    private IMap<String, Instant> failTrackerMap;

    @Autowired
    @Qualifier("getHeartbeatTimestampMap")
    private IMap<String, Instant> heartbeatTimestampMap;

    @Autowired
    @Qualifier("getInitialDeliveryTimestampMap")
    private IMap<String, Instant> initialDeliveryTimestampMap;

    @Autowired
    @Qualifier("getInitialDeliveryPendingMap")
    private IMap<String, Instant> initialDeliveryPendingMap;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 10000;

    @Value("${anshar.outbound.heartbeatinterval.maximum}")
    private long maximumHeartbeatInterval = 300000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    @Value("${anshar.outbound.error.failtracker.ttl.minutes:30}")
    private long failTrackerTtlMinutes = 30;

    // A subscription whose deliveries keep failing is cancelled after this many heartbeat intervals...
    @Value("${anshar.outbound.error.graceperiod.factor:3}")
    private long failGracePeriodFactor = 3;

    // ...but never sooner than this
    @Value("${anshar.outbound.error.graceperiod.minimum:PT3M}")
    private Duration failGracePeriodMinimum = Duration.ofMinutes(3);

    // A re-subscription that replaces a subscription to the same address gets no new initial delivery if one was
    // sent to that address more recently than this - the recipient already has the data, and gets all changes
    @Value("${anshar.outbound.resubscribe.initialdelivery.minimum.interval:PT5M}")
    private Duration resubscribeInitialDeliveryInterval = Duration.ofMinutes(5);

    // Updates to a subscription are collected and pushed once per window - from the request (VM UpdateInterval),
    // else per type, read at push time so existing subscriptions follow it too. 0 pushes every update at once.
    @Value("${anshar.outbound.batch.window.et:0s}")
    private Duration batchWindowEt = Duration.ZERO;
    @Value("${anshar.outbound.batch.window.vm:0s}")
    private Duration batchWindowVm = Duration.ZERO;
    @Value("${anshar.outbound.batch.window.sx:0s}")
    private Duration batchWindowSx = Duration.ZERO;
    @Value("${anshar.outbound.batch.window.fm:0s}")
    private Duration batchWindowFm = Duration.ZERO;
    @Value("${anshar.outbound.batch.window.max:30s}")
    private Duration batchWindowMax = Duration.ofSeconds(30);

    @Value("${anshar.default.max.elements.per.delivery:1000}")
    private int maxElementsPerDelivery = 1000;

    @Value("${anshar.outbound.pubsub.vm.topic.enabled}")
    private boolean pushToVmTopicEnabled;
    @Value("${anshar.outbound.pubsub.et.topic.enabled}")
    private boolean pushToEtTopicEnabled;
    @Value("${anshar.outbound.pubsub.sx.topic.enabled}")
    private boolean pushToSxTopicEnabled;
    @Value("${anshar.outbound.pubsub.fm.topic.enabled}")
    private boolean pushToFmTopicEnabled;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    @Autowired
    private SiriTopicPublisher siriTopicPublisher;

    ExecutorService outboundSenderExecutorService = Executors.newFixedThreadPool(100);

    @Produce(value = "direct:send.to.pubsub.topic.estimated_timetable")
    protected ProducerTemplate siriEtTopicProducer;

    @Produce(value = "direct:send.to.pubsub.topic.vehicle_monitoring")
    protected ProducerTemplate siriVmTopicProducer;

    @Produce(value = "direct:send.to.pubsub.topic.situation_exchange")
    protected ProducerTemplate siriSxTopicProducer;

    @Produce(value = "direct:send.to.pubsub.topic.facility_monitoring")
    protected ProducerTemplate siriFmTopicProducer;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private SiriHelper siriHelper;

    private final PrometheusMetricsService metricsService;

    private ScheduledExecutorService batchFlushScheduler;

    private OutboundBatcher batcher;

    public ServerSubscriptionManager(PrometheusMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostConstruct
    void startBatcher() {
        AtomicInteger threadNumber = new AtomicInteger();
        batchFlushScheduler = Executors.newScheduledThreadPool(BATCH_FLUSH_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "outbound-batch-flush-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        batcher = new OutboundBatcher(batchFlushScheduler, this::flushBatch, maxElementsPerDelivery);
    }

    /**
     * Runs before CamelRouteManager is destroyed (see @DependsOn), so the flushed batches are still pushed
     */
    @PreDestroy
    void stopBatcher() {
        batcher.flushAll();
        batchFlushScheduler.shutdown();
    }

    public Collection getSubscriptions() {
        return Collections.unmodifiableCollection(subscriptions.values());
    }

    public JSONArray getSubscriptionsAsJson() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        JSONArray stats = new JSONArray();

        for (String key : subscriptions.keySet()) {

            OutboundSubscriptionSetup subscription = subscriptions.get(key);

            JSONObject obj = new JSONObject();
            obj.put("subscriptionRef", key);
            obj.put("subscriptionType",""+subscription.getSubscriptionType());
            obj.put("version", subscription.getSiriVersion().equals(SiriValidator.Version.VERSION_2_1) ? "2.1" : "2.0");
            obj.put("address", subscription.getAddress());
            obj.put("heartbeatInterval", (subscription.getHeartbeatInterval()/1000) + " s");
            obj.put("updateInterval", (batchWindow(subscription)/1000.0) + " s");
            obj.put("datasetId",subscription.getDatasetId()!=null ? subscription.getDatasetId():"");
            obj.put("requestReceived", formatter.format(subscription.getRequestTimestamp()));
            obj.put("initialTerminationTime",formatter.format(subscription.getInitialTerminationTime()));
            obj.put("clientTrackingName",subscription.getClientTrackingName()!=null ? subscription.getClientTrackingName():"");

            stats.add(obj);
        }

        return stats;
    }

    public Siri handleSubscriptionRequest(Siri siri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName) {

        OutboundSubscriptionSetup subscription = createSubscription(siri, datasetId, outboundIdMappingPolicy, clientTrackingName);

        String errorText = validateSubscription(subscription);
        if (errorText != null) {
            return siriObjectFactory.createSubscriptionResponse(
                    subscription.getSubscriptionId(),
                    false,
                    errorText,
                    resolveSiriVersionStr(subscription.getSiriVersion())
            );
        }

        // Added before the replaced subscriptions are removed, so that no update falls between them
        addSubscription(subscription);
        final InitialDeliveryState inherited = replaceSubscriptionsWithSameContent(subscription);

        Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(
                subscription.getSubscriptionId(),
                true,
                null,
                resolveSiriVersionStr(subscription.getSiriVersion()))
                ;

        startInitialDelivery(subscription, inherited);

        return subscriptionResponse;
    }

    /**
     * @return why the subscription is rejected, or null if it is accepted
     */
    private String validateSubscription(OutboundSubscriptionSetup subscription) {
        String errorText = null;

        if (subscription.getAddress() == null) {
            errorText = errorConsumerAddressMissing;
        } else if (subscription.getInitialTerminationTime() == null || subscription.getInitialTerminationTime().isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            errorText = initialTerminationTimePassed;
        }

        final OutboundSubscriptionSetup existing = subscriptions.get(subscription.getSubscriptionId());
        if (existing != null && subscription.getSubscriptionType() != existing.getSubscriptionType()) {
            errorText = "A different subscription with id=" + subscription.getSubscriptionId() + " already exists";
        }

        final Instant firstFail = failTrackerMap.get(subscription.getSubscriptionId());
        if (errorText == null && firstFail != null) {
            // Pushing to this subscription has recently failed - only accept if the recipient is reachable now.
            // The fail-tracker is kept until a delivery succeeds, so that the subscription is cancelled on the next failure.
            String probeError = camelRouteManager.probeConsumerAddress(subscription.getAddress());
            if (probeError != null) {
                logger.info("Rejecting subscription {} - previous deliveries failed, and ConsumerAddress is still unreachable: {}", subscription, probeError);
                errorText = "ConsumerAddress is unreachable: " + probeError;
                // Keep remembering the failures as long as the recipient keeps trying
                failTrackerMap.set(subscription.getSubscriptionId(), firstFail, failTrackerTtlMinutes, TimeUnit.MINUTES);
            }
        }
        return errorText;
    }

    /**
     * Sends all data to a new subscription - unless it replaced a subscription whose initial delivery it can rely on
     */
    private void startInitialDelivery(OutboundSubscriptionSetup subscription, InitialDeliveryState inherited) {
        if (inherited == null) {
            sendInitialDelivery(subscription);
        } else if (inherited.delivered()) {
            logger.info("Skipping initial delivery to re-subscription {} - all data was delivered to {} at {}",
                    subscription.getSubscriptionId(), subscription.getAddress(), inherited.time());
            rememberInitialDelivery(initialDeliveryTimestampMap, subscription.getSubscriptionId(), inherited.time());
        } else {
            // The outcome of that delivery decides whether this subscription needs its own, see initialDeliveryCompleted
            logger.info("Skipping initial delivery to re-subscription {} - all data is being sent to {} since {}",
                    subscription.getSubscriptionId(), subscription.getAddress(), inherited.time());
            rememberInitialDelivery(initialDeliveryPendingMap, subscription.getSubscriptionId(), inherited.time());
        }
    }

    private void sendInitialDelivery(OutboundSubscriptionSetup subscription) {
        logger.debug("Find initial delivery for {}", subscription);
        // The initial delivery contains all buffered updates - also those held for a replaced or terminated
        // subscription with the same content, which a flush would otherwise redirect to this one after the dump
        batcher.clear(subscription.getSubscriptionId());
        batcher.clearIf(buffered -> hasSameContent(buffered, subscription));
        Siri delivery = siriHelper.findInitialDeliveryData(subscription);

        if (delivery != null) {
            logger.info("Sending initial delivery to {}", subscription.getAddress());
            rememberInitialDelivery(initialDeliveryPendingMap, subscription.getSubscriptionId(), Instant.now());
            camelRouteManager.pushInitialDelivery(delivery, subscription,
                    outcome -> initialDeliveryCompleted(subscription, outcome));
        } else {
            logger.info("No initial delivery found for {}", subscription);
        }
    }

    /**
     * The subscription may have been replaced by a re-subscription while its initial delivery was sent - the outcome
     * then applies to the subscription that replaced it, which skipped its own initial delivery.
     */
    private void initialDeliveryCompleted(OutboundSubscriptionSetup subscription, CamelRouteManager.Outcome outcome) {
        initialDeliveryPendingMap.delete(subscription.getSubscriptionId());
        OutboundSubscriptionSetup current = subscriptions.containsKey(subscription.getSubscriptionId())
                ? subscription : findSubscriptionWithSameContent(subscription);
        if (outcome == CamelRouteManager.Outcome.DELIVERED) {
            // Only a delivery the recipient took in full can stand in for a later re-subscription
            Instant delivered = Instant.now();
            rememberInitialDelivery(initialDeliveryTimestampMap, subscription.getSubscriptionId(), delivered);
            if (current != null && current != subscription) {
                initialDeliveryPendingMap.delete(current.getSubscriptionId());
                rememberInitialDelivery(initialDeliveryTimestampMap, current.getSubscriptionId(), delivered);
            }
        } else if (current != null && current != subscription
                && initialDeliveryPendingMap.containsKey(current.getSubscriptionId())) {
            logger.info("Initial delivery to {} was not delivered ({}) - sending one to re-subscription {}",
                    subscription.getSubscriptionId(), outcome, current.getSubscriptionId());
            sendInitialDelivery(current);
        }
    }

    private OutboundSubscriptionSetup createSubscription(Siri siri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName) {

        SubscriptionRequest subscriptionRequest = siri.getSubscriptionRequest();
        SiriDataType subscriptionType = getSubscriptionType(subscriptionRequest);
        OutboundSubscriptionSetup subscription = new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                subscriptionType,
                subscriptionRequest.getConsumerAddress() != null ? subscriptionRequest.getConsumerAddress():subscriptionRequest.getAddress(),
                getHeartbeatInterval(subscriptionRequest),
                getChangeBeforeUpdates(subscriptionRequest),
                siriHelper.getFilter(subscriptionRequest),
                mappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy),
                findSubscriptionIdentifier(subscriptionRequest),
                findSubscriberIdentifier(subscriptionRequest),
                findInitialTerminationTime(subscriptionRequest),
                datasetId,
                clientTrackingName,
                resolveSiriVersion(siri, outboundIdMappingPolicy)
                );
        subscription.setUpdateInterval(resolveUpdateInterval(subscriptionRequest, subscriptionType));
        return subscription;
    }

    /**
     * @return the window (millis) to collect updates for before pushing them - VM UpdateInterval from the request,
     * else the configured window for the type, at most anshar.outbound.batch.window.max
     */
    long resolveUpdateInterval(SubscriptionRequest subscriptionRequest, SiriDataType type) {
        Duration window = null;
        if (type == SiriDataType.VEHICLE_MONITORING
                && SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            window = subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getUpdateInterval();
        }
        if (window == null) {
            window = configuredBatchWindow(type);
        }
        return clampBatchWindow(window);
    }

    /**
     * @return the window (millis) a subscription is batched with - its own, else the one configured for its type
     * now. Subscriptions created before a window was configured get it without having to re-subscribe.
     */
    long batchWindow(OutboundSubscriptionSetup subscription) {
        if (subscription.getUpdateInterval() > 0) {
            return subscription.getUpdateInterval();
        }
        return clampBatchWindow(configuredBatchWindow(subscription.getSubscriptionType()));
    }

    private long clampBatchWindow(Duration window) {
        return Math.max(0, Math.min(window.toMillis(), batchWindowMax.toMillis()));
    }

    private Duration configuredBatchWindow(SiriDataType type) {
        if (type == null) {
            return Duration.ZERO;
        }
        return switch (type) {
            case ESTIMATED_TIMETABLE -> batchWindowEt;
            case VEHICLE_MONITORING -> batchWindowVm;
            case SITUATION_EXCHANGE -> batchWindowSx;
            case FACILITY_MONITORING -> batchWindowFm;
            default -> Duration.ZERO;
        };
    }

    private String findSubscriberIdentifier(SubscriptionRequest subscriptionRequest) {

        if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
        !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        } else if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests() != null &&
                !subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        } else if (subscriptionRequest.getSituationExchangeSubscriptionRequests() != null &&
                !subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        } else if (subscriptionRequest.getFacilityMonitoringSubscriptionRequests() != null &&
                !subscriptionRequest.getFacilityMonitoringSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        }
        return subscriptionRequest.getRequestorRef().getValue();
    }

    private static SiriValidator.Version resolveSiriVersion(Siri siri, OutboundIdMappingPolicy outboundIdMappingPolicy) {

        SubscriptionRequest subscriptionRequest = siri.getSubscriptionRequest();
        String version = null;
        if (siri.getVersion() != null) {
            version = siri.getVersion();
        } else if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            version = subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getSituationExchangeRequest().getVersion();
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            version = subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getVehicleMonitoringRequest().getVersion();
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            version = subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getEstimatedTimetableRequest().getVersion();
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {
            version = subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0).getFacilityMonitoringRequest().getVersion();
        }

        // Subscriber requests SIRI 2.1 version in request
        if ("2.1".equals(version)) {
            return SiriValidator.Version.VERSION_2_1;
        }

        // Checking header-override
        SiriValidator.Version siriVersion = outboundIdMappingPolicy.equals(OutboundIdMappingPolicy.SIRI_2_1) ?
                SiriValidator.Version.VERSION_2_1 : SiriValidator.Version.VERSION_2_0;
        return siriVersion;
    }

    // public for unittest
    public long getHeartbeatInterval(SubscriptionRequest subscriptionRequest) {
        long heartbeatInterval = 0;
        if (subscriptionRequest.getSubscriptionContext() != null &&
                subscriptionRequest.getSubscriptionContext().getHeartbeatInterval() != null) {
            Duration interval = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();
            heartbeatInterval = interval.toMillis();
        }
        heartbeatInterval = Math.max(heartbeatInterval, minimumHeartbeatInterval);
        heartbeatInterval = Math.min(heartbeatInterval, maximumHeartbeatInterval);

        return heartbeatInterval;
    }

    private SiriDataType getSubscriptionType(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return SiriDataType.SITUATION_EXCHANGE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return SiriDataType.VEHICLE_MONITORING;
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return SiriDataType.ESTIMATED_TIMETABLE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {
            return SiriDataType.FACILITY_MONITORING;
        }
        return null;
    }

    private long getChangeBeforeUpdates(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        }
        return 0;
    }

    private long getMilliSeconds(Duration changeBeforeUpdates) {
        if (changeBeforeUpdates != null) {
            return changeBeforeUpdates.toMillis();
        }
        return 0;
    }

    /** When all data was delivered to a subscription, or since when it is being sent */
    private record InitialDeliveryState(Instant time, boolean delivered) {
    }

    /**
     * Removes existing subscriptions from the same client that would deliver exactly the same data to the same
     * address as the new one. Some clients re-subscribe with a new SubscriptionIdentifier without terminating the
     * previous subscription - without this, every re-subscription multiplies the traffic to the recipient.
     *
     * @return the initial delivery of a replaced subscription that the new subscription can rely on instead of its
     * own - null if it needs its own
     */
    private InitialDeliveryState replaceSubscriptionsWithSameContent(OutboundSubscriptionSetup subscription) {
        boolean replacedFailing = false;
        Instant delivered = null;
        Instant pending = null;
        for (OutboundSubscriptionSetup existing : subscriptions.values()) {
            if (!existing.getSubscriptionId().equals(subscription.getSubscriptionId()) && hasSameContent(existing, subscription)) {
                logger.info("Subscription {} replaces subscription {} with the same address and content",
                        subscription.getSubscriptionId(), existing.getSubscriptionId());

                // Updates to a failing subscription may have been lost - its recipient needs all data again
                replacedFailing |= failTrackerMap.containsKey(existing.getSubscriptionId());
                delivered = latest(delivered, initialDeliveryTimestampMap.get(existing.getSubscriptionId()));
                pending = latest(pending, initialDeliveryPendingMap.get(existing.getSubscriptionId()));
                removeSubscription(existing.getSubscriptionId());
            }
        }
        if (replacedFailing || !isResubscribeInitialDeliveryIntervalEnabled()) {
            return null;
        }
        Instant notBefore = Instant.now().minus(resubscribeInitialDeliveryInterval);
        if (delivered != null && delivered.isAfter(notBefore)) {
            return new InitialDeliveryState(delivered, true);
        }
        if (pending != null && pending.isAfter(notBefore)) {
            return new InitialDeliveryState(pending, false);
        }
        return null;
    }

    private static boolean hasSameContent(OutboundSubscriptionSetup a, OutboundSubscriptionSetup b) {
        return Objects.equals(a.getAddress(), b.getAddress())
                && a.getSubscriptionType() == b.getSubscriptionType()
                && Objects.equals(a.getDatasetId(), b.getDatasetId())
                && Objects.equals(a.getFilterMap(), b.getFilterMap())
                && Objects.equals(a.getRequestorRef(), b.getRequestorRef())
                && a.getSiriVersion() == b.getSiriVersion()
                && a.getChangeBeforeUpdates() == b.getChangeBeforeUpdates();
    }

    private OutboundSubscriptionSetup findSubscriptionWithSameContent(OutboundSubscriptionSetup subscription) {
        for (OutboundSubscriptionSetup existing : subscriptions.values()) {
            if (hasSameContent(existing, subscription)) {
                return existing;
            }
        }
        return null;
    }

    private static Instant latest(Instant a, Instant b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    private boolean isResubscribeInitialDeliveryIntervalEnabled() {
        return resubscribeInitialDeliveryInterval.isPositive();
    }

    /** Kept until the interval has passed, counted from the given time */
    private void rememberInitialDelivery(IMap<String, Instant> map, String subscriptionId, Instant time) {
        if (!isResubscribeInitialDeliveryIntervalEnabled()) {
            return;
        }
        long ttlMillis = resubscribeInitialDeliveryInterval.toMillis() - time.until(Instant.now(), MILLIS);
        if (ttlMillis > 0) {
            map.set(subscriptionId, time, ttlMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void addSubscription(OutboundSubscriptionSetup subscription) {
        subscriptions.put(subscription.getSubscriptionId(), subscription);
    }

    private OutboundSubscriptionSetup removeSubscription(String subscriptionId) {
        logger.info("Removing subscription {}", subscriptionId);
        // Fail-tracker is kept (with TTL) so that a re-subscription to a failing recipient is detected
        heartbeatTimestampMap.remove(subscriptionId);
        initialDeliveryTimestampMap.delete(subscriptionId);
        initialDeliveryPendingMap.delete(subscriptionId);
        return subscriptions.remove(subscriptionId);
    }

    private String findSubscriptionIdentifier(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = subscriptionRequest.
                    getSituationExchangeSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(situationExchangeSubscriptionStructure);

        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionStructure =
                    subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(vehicleMonitoringSubscriptionStructure);

        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            EstimatedTimetableSubscriptionStructure estimatedTimetableSubscriptionStructure =
                    subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(estimatedTimetableSubscriptionStructure);
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {

            FacilityMonitoringSubscriptionStructure facilityMonitoringSubscriptionStructure =
                    subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(facilityMonitoringSubscriptionStructure);
        }
        return null;
    }

    private String getSubscriptionIdentifier(AbstractSubscriptionStructure subscriptionStructure) {
        if (subscriptionStructure != null && subscriptionStructure.getSubscriptionIdentifier() != null) {
            return subscriptionStructure.getSubscriptionIdentifier().getValue();
        }
        return null;
    }

    private ZonedDateTime findInitialTerminationTime(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            return subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        }
        return null;
    }

    /**
     * Terminates all subscriptions for a given subscriberRef.
     * @param subscriberRef
     * @param postResponse
     */
    public void terminateSubscriptions(String subscriberRef, boolean postResponse) {
        for (OutboundSubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getRequestorRef().equals(subscriberRef)) {
                terminateSubscription(subscription.getSubscriptionId(), postResponse);
            }
        }
    }

    /**
     * Terminates a subscription based on the given subscriptionRef.
     * @param subscriptionRef
     * @param postResponse
     */
    public void terminateSubscription(String subscriptionRef, boolean postResponse) {
        OutboundSubscriptionSetup subscriptionRequest = removeSubscription(subscriptionRef);

        if (subscriptionRequest != null) {
            if (postResponse) {
                Siri terminateSubscriptionResponse = siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
                logger.info("Sending TerminateSubscriptionResponse to {}", subscriptionRequest.getAddress());

                // Subscription is already removed - push regardless
                camelRouteManager.pushSiriData(terminateSubscriptionResponse, subscriptionRequest, true, false);
            } else {
                logger.info("Subscription terminated, but no response was sent");
            }
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        return siriObjectFactory.createCheckStatusResponse(checkStatusRequest);
    }

    public void pushUpdatesAsync(SiriDataType datatype, List updates, String datasetId) {

        final String breadcrumbId = MDC.get("camel.breadcrumbId");
        final long storedAt = System.currentTimeMillis();

        switch (datatype) {
            case ESTIMATED_TIMETABLE:
                outboundSenderExecutorService.execute(() -> pushUpdatedEstimatedTimetables(updates, datasetId, breadcrumbId, storedAt));
                break;
            case SITUATION_EXCHANGE:
                outboundSenderExecutorService.execute(() -> pushUpdatedSituations(updates, datasetId, breadcrumbId, storedAt));
                break;
            case VEHICLE_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedVehicleActivities(updates, datasetId, breadcrumbId, storedAt));
                break;
            case FACILITY_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedFacilityMonitoring(updates, datasetId, breadcrumbId, storedAt));
                break;
            default:
                // Ignore
                break;
        }
    }

    /** Headers for the outbound topic routes: the codespace, and when the updates were stored */
    private static Map<String, Object> topicHeaders(String datasetId, long storedAt) {
        return Map.of(
                CODESPACE_ID_KAFKA_HEADER_NAME, datasetId,
                PrometheusMetricsService.STORED_AT_HEADER, storedAt);
    }

    private void pushUpdatedVehicleActivities(
        List<VehicleActivityStructure> addedOrUpdated, String datasetId, String breadcrumbId, long storedAt
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        // Built once, and only if a topic or a recipient without update interval needs it
        Supplier<Siri> delivery = Suppliers.memoize(() -> siriObjectFactory.createVMServiceDelivery(addedOrUpdated));

        if (pushToVmTopicEnabled) {
            siriVmTopicProducer.asyncRequestBodyAndHeaders(siriVmTopicProducer.getDefaultEndpoint(), delivery.get(), topicHeaders(datasetId, storedAt));
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
            .values()
            .stream()
            .filter(subscriptionRequest -> (
                    subscriptionRequest.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)
                        && (
                        subscriptionRequest.getDatasetId() == null || (
                            subscriptionRequest
                                .getDatasetId()
                                .equals(datasetId)
                        )
                    )
                )

            )
            .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} VM updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            pushToRecipients(recipients, addedOrUpdated, breadcrumbId, delivery);
        }

        MDC.remove("camel.breadcrumbId");
    }


    private void pushUpdatedSituations(
        List<PtSituationElement> addedOrUpdated, String datasetId, String breadcrumbId, long storedAt
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        // Built once, and only if a topic or a recipient without update interval needs it
        Supplier<Siri> delivery = Suppliers.memoize(() -> siriObjectFactory.createSXServiceDelivery(addedOrUpdated));

        // With direct publishing on, SiriTopicPublisher publishes SX to the topics instead
        if (pushToSxTopicEnabled && !siriTopicPublisher.handles(SiriDataType.SITUATION_EXCHANGE)) {
            siriSxTopicProducer.asyncRequestBodyAndHeaders(siriSxTopicProducer.getDefaultEndpoint(), delivery.get(), topicHeaders(datasetId, storedAt));
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
            .values()
            .stream()
            .filter(subscriptionRequest -> (
                    subscriptionRequest.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)
                        && (
                        subscriptionRequest.getDatasetId() == null || (
                            subscriptionRequest
                                .getDatasetId()
                                .equals(datasetId)
                        )
                    )
                )

            )
            .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} SX updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            pushToRecipients(recipients, addedOrUpdated, breadcrumbId, delivery);
        }

        MDC.remove("camel.breadcrumbId");
    }

    private void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated, String datasetId, String breadcrumbId, long storedAt) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        MDC.put("camel.breadcrumbId", breadcrumbId);

        // Built once, and only if a topic or a recipient without update interval needs it
        Supplier<Siri> delivery = Suppliers.memoize(() -> siriObjectFactory.createETServiceDelivery(addedOrUpdated));

        // With direct publishing on, SiriTopicPublisher publishes ET to the topics instead
        if (pushToEtTopicEnabled && !siriTopicPublisher.handles(SiriDataType.ESTIMATED_TIMETABLE)) {
            siriEtTopicProducer.asyncRequestBodyAndHeaders(siriEtTopicProducer.getDefaultEndpoint(), delivery.get(), topicHeaders(datasetId, storedAt));
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
            .values()
            .stream()
            .filter(subscriptionRequest -> (
                    subscriptionRequest.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)
                        && (
                        subscriptionRequest.getDatasetId() == null || (
                            subscriptionRequest
                                .getDatasetId()
                                .equals(datasetId)
                        )
                    )
                )

            )
            .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} ET updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            pushToRecipients(recipients, addedOrUpdated, breadcrumbId, delivery);
        }
        MDC.remove("camel.breadcrumbId");
    }

    private void pushUpdatedFacilityMonitoring(
            List<FacilityConditionStructure> addedOrUpdated, String datasetId, String breadcrumbId, long storedAt
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        // Built once, and only if a topic or a recipient without update interval needs it
        Supplier<Siri> delivery = Suppliers.memoize(() -> siriObjectFactory.createFMServiceDelivery(addedOrUpdated));

        if (pushToFmTopicEnabled) {
            siriFmTopicProducer.asyncRequestBodyAndHeaders(siriFmTopicProducer.getDefaultEndpoint(), delivery.get(), topicHeaders(datasetId, storedAt));
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.FACILITY_MONITORING)
                                        && (
                                        subscriptionRequest.getDatasetId() == null || (
                                                subscriptionRequest
                                                        .getDatasetId()
                                                        .equals(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} FM updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            pushToRecipients(recipients, addedOrUpdated, breadcrumbId, delivery);
        }

        MDC.remove("camel.breadcrumbId");
    }

    /**
     * Recipients with an update interval get the updates in their buffer, the others get the delivery now
     */
    private void pushToRecipients(List<OutboundSubscriptionSetup> recipients, List<?> addedOrUpdated,
                                  String breadcrumbId, Supplier<Siri> delivery) {
        for (OutboundSubscriptionSetup recipient : recipients) {
            long window = batchWindow(recipient);
            if (window > 0) {
                try {
                    batcher.add(recipient, window, addedOrUpdated, breadcrumbId);
                } catch (RejectedExecutionException e) {
                    // Scheduler is shut down - send now instead of losing the update
                    logger.debug("Batch scheduler is shut down - pushing to {} immediately", recipient.getSubscriptionId());
                    pushImmediately(delivery, recipient);
                } catch (RuntimeException e) {
                    // Not buffered - send now, and go on with the other recipients
                    logger.warn("Batching failed for {} - pushing immediately", recipient.getSubscriptionId(), e);
                    pushImmediately(delivery, recipient);
                }
            } else {
                camelRouteManager.pushSiriData(delivery.get(), recipient, false);
            }
        }
    }

    /**
     * Fallback when batching fails - a failure here must not stop the push to the other recipients
     */
    private void pushImmediately(Supplier<Siri> delivery, OutboundSubscriptionSetup recipient) {
        try {
            camelRouteManager.pushSiriData(delivery.get(), recipient, false);
        } catch (RuntimeException e) {
            logger.warn("Pushing to {} failed", recipient.getSubscriptionId(), e);
        }
    }

    /**
     * Pushes a batch to the subscription as it is now - the batch may have been started for a subscription that has
     * since been changed or replaced by a re-subscription (on any pod). A batch for a removed subscription is dropped.
     */
    void flushBatch(OutboundSubscriptionSetup batchSubscription, List<Object> objects, String breadcrumbId) {
        OutboundSubscriptionSetup target = subscriptions.get(batchSubscription.getSubscriptionId());
        if (target == null) {
            target = findSubscriptionWithSameContent(batchSubscription);
        }
        if (target == null) {
            logger.debug("Dropping {} buffered updates - subscription {} no longer exists",
                    objects.size(), batchSubscription.getSubscriptionId());
            return;
        }

        final SiriDataType type = target.getSubscriptionType();
        final Siri delivery = switch (type) {
            case ESTIMATED_TIMETABLE -> siriObjectFactory.createETServiceDelivery((List) objects);
            case VEHICLE_MONITORING -> siriObjectFactory.createVMServiceDelivery((List) objects);
            case SITUATION_EXCHANGE -> siriObjectFactory.createSXServiceDelivery((List) objects);
            case FACILITY_MONITORING -> siriObjectFactory.createFMServiceDelivery((List) objects);
            default -> null;
        };
        if (delivery == null) {
            return;
        }

        // A size-triggered flush runs within a push of new updates - keep its breadcrumb
        final String previousBreadcrumbId = MDC.get("camel.breadcrumbId");
        MDC.put("camel.breadcrumbId", breadcrumbId);
        try {
            logger.debug("Pushing batch of {} updates to subscription {}", objects.size(), target.getSubscriptionId());
            camelRouteManager.pushSiriData(delivery, target, false);
            metricsService.recordOutboundBatch(type, objects.size());
        } finally {
            if (previousBreadcrumbId != null) {
                MDC.put("camel.breadcrumbId", previousBreadcrumbId);
            } else {
                MDC.remove("camel.breadcrumbId");
            }
        }
    }

    public void pushFailedForSubscription(String subscriptionId) {
        OutboundSubscriptionSetup outboundSubscriptionSetup = subscriptions.get(subscriptionId);
        if (outboundSubscriptionSetup != null) {

            long gracePeriod = Math.max(failGracePeriodFactor * outboundSubscriptionSetup.getHeartbeatInterval(),
                    failGracePeriodMinimum.toMillis());

            Instant firstFail = failTrackerMap.getOrDefault(subscriptionId, Instant.now());

            long terminationTime = firstFail.until(Instant.now(), MILLIS);
            if (terminationTime > gracePeriod) {
                logger.info("Cancelling outbound subscription {} that has failed for {}s.", subscriptionId, terminationTime/1000);
                removeSubscription(subscriptionId);
            } else {
                logger.info("Outbound subscription {} has not responded for {}s, will be cancelled after {}s.", subscriptionId, terminationTime/1000, gracePeriod/1000);
                failTrackerMap.set(subscriptionId, firstFail, failTrackerTtlMinutes, TimeUnit.MINUTES);
            }
        }
    }

    public void clearFailTracker(String subscriptionId) {
        if (failTrackerMap.containsKey(subscriptionId)) {
            logger.info("Subscription {} is now responding - clearing failtracker", subscriptionId);
            failTrackerMap.delete(subscriptionId);
        }
    }
}
