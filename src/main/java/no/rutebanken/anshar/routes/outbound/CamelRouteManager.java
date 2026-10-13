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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.entur.siri.validator.SiriValidator;
import org.entur.siri21.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SubscriptionRefStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static no.rutebanken.anshar.routes.RestRouteBuilder.downgradeSiriVersion;

@Service
public class CamelRouteManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SiriHelper siriHelper;

    @Autowired
    ServerSubscriptionManager subscriptionManager;

    @Autowired
    PrometheusMetricsService metricsService;

    @Value("${anshar.default.max.elements.per.delivery:1000}")
    private int maximumSizePerDelivery;

    @Value("${anshar.default.max.threads.per.outbound.subscription:5}")
    private int maximumThreadsPerOutboundSubscription;

    @Value("${anshar.default.max.queue.size.per.outbound.subscription:1000}")
    private int maximumQueueSizePerOutboundSubscription;

    @Value("${anshar.outbound.timeout.socket:15000}")
    private int requestTimeoutMillis;

    @Value("${anshar.outbound.executor.cleanup.interval.seconds:60}")
    private int executorCleanupIntervalSeconds;

    private Map<String, ExecutorService> threadFactoryMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService executorCleanupScheduler;

    private volatile boolean shuttingDown = false;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();


    @PostConstruct
    private void initThreadMetrics() {
        metricsService.registerOutboundThreadFactoryMap(threadFactoryMap);
    }

    @PostConstruct
    private void initExecutorCleanup() {
        // Subscriptions may be terminated or expire on any node - periodically release the local resources
        executorCleanupScheduler = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("outbound-executor-cleanup").setDaemon(true).build());
        executorCleanupScheduler.scheduleWithFixedDelay(() -> {
            try {
                removeDeadSubscriptionExecutors(subscriptionManager);
            } catch (Throwable t) {
                //Catch everything to avoid executor being killed
                logger.info("Exception caught when cleaning up outbound executors", t);
            }
        }, executorCleanupIntervalSeconds, executorCleanupIntervalSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    private void shutdownExecutors() {
        shuttingDown = true;
        if (executorCleanupScheduler != null) {
            executorCleanupScheduler.shutdownNow();
        }
        threadFactoryMap.values().forEach(ExecutorService::shutdownNow);
        threadFactoryMap.clear();
    }

    /**
     * Splits SIRI-data if applicable, and pushes data to external subscription
     * @param payload
     * @param subscriptionRequest
     */
    void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody) {
        pushSiriData(payload, subscriptionRequest, logBody, true);
    }

    /**
     * Splits SIRI-data if applicable, and pushes data to external subscription
     * @param payload
     * @param subscriptionRequest
     * @param requireActiveSubscription if false, data is pushed even if the subscription no longer exists - e.g. TerminateSubscriptionResponse
     */
    void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody, boolean requireActiveSubscription) {
        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }
        final String breadcrumbId = MDC.get("camel.breadcrumbId");
        final String subscriptionId = subscriptionRequest.getSubscriptionId();

        final Runnable pushTask = () -> {

            try {
                MDC.put("camel.breadcrumbId", breadcrumbId);
                MDC.put("subscriptionId", subscriptionId);
                if (requireActiveSubscription && !subscriptionManager.subscriptions.containsKey(subscriptionId)) {
                    // Short circuit if subscription has been terminated while waiting
                    return;
                }

                Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

                metricsService.countOutgoingData(filteredPayload, SubscriptionSetup.SubscriptionMode.SUBSCRIBE);

                int deliverySize = this.maximumSizePerDelivery;
                if (subscriptionRequest.getDatasetId() != null) {
                    deliverySize = Integer.MAX_VALUE;
                }

                List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize);

                if (splitSiri.size() > 1) {
                    logger.info("Object split into {} deliveries for subscription {}.", splitSiri.size(), subscriptionRequest);
                }

                for (Siri siri : splitSiri) {
                    addSubscriptionRefToServiceDelivery(siri, subscriptionId);
                    int responseCode = postDataToSubscription(siri, subscriptionRequest, logBody);

                    metricsService.markPostToSubscription(subscriptionRequest.getSubscriptionType(),
                            SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                            subscriptionId,
                            responseCode);

                    if (responseCode > 299) {
                        logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, responseCode);
                        subscriptionManager.pushFailedForSubscription(subscriptionId);
                    } else {
                        subscriptionManager.clearFailTracker(subscriptionId);
                    }
                }
            } catch (Exception e) {
                if (shuttingDown) {
                    // Interrupted by shutdown - not caused by the subscriber
                    return;
                }
                logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, e.toString());

                int statusCode = -1;
                if (e.getCause() instanceof SocketException) {
                    logger.info("Recipient is unreachable - ignoring");
                } else {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                        if (e.getCause() instanceof HttpOperationFailedException) {
                            statusCode = ((HttpOperationFailedException) e.getCause()).getStatusCode();
                        }
                    }
                    logger.info("Exception caught when pushing SIRI-data: {}", msg);
                }
                subscriptionManager.pushFailedForSubscription(subscriptionId);

                metricsService.markPostToSubscription(subscriptionRequest.getSubscriptionType(),
                        SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                        subscriptionId,
                        statusCode);

            } finally {
                MDC.remove("camel.breadcrumbId");
                MDC.remove("subscriptionId");
            }
        };

        ExecutorService executorService = getOrCreateExecutorService(subscriptionRequest);
        try {
            executorService.execute(pushTask);
        } catch (RejectedExecutionException e) {
            // Executor was shut down by the cleanup-job after it was fetched - retry with a new executor
            threadFactoryMap.remove(subscriptionId, executorService);
            if (shuttingDown) {
                return;
            }
            try {
                getOrCreateExecutorService(subscriptionRequest).execute(pushTask);
            } catch (RejectedExecutionException ex) {
                logger.warn("Unable to push data for subscription {} - executor has been shut down", subscriptionId);
            }
        }
    }

    private static void addSubscriptionRefToServiceDelivery(Siri siri, String subscriptionId) {
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            //ignore
            return;
        }
        if (siri.getServiceDelivery() != null) {
            SubscriptionRefStructure subscriptionRef = new SubscriptionRefStructure();
            subscriptionRef.setValue(subscriptionId);
            if (siri.getServiceDelivery().getSituationExchangeDeliveries() != null &&
                    !siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
                siri.getServiceDelivery().getSituationExchangeDeliveries().forEach(
                        delivery -> delivery.setSubscriptionRef(subscriptionRef)
                );
            }
            if (siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null &&
                    !siri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
                siri.getServiceDelivery().getVehicleMonitoringDeliveries().forEach(
                        delivery -> delivery.setSubscriptionRef(subscriptionRef)
                );
            }
            if (siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null &&
                    !siri.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
                siri.getServiceDelivery().getEstimatedTimetableDeliveries().forEach(
                        delivery -> delivery.setSubscriptionRef(subscriptionRef)
                );
            }
        }
    }

    private ExecutorService getOrCreateExecutorService(OutboundSubscriptionSetup subscriptionRequest) {

        return threadFactoryMap.computeIfAbsent(subscriptionRequest.getSubscriptionId(), subscriptionId -> {
            ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("outbound-"+subscriptionId).build();

            // Queue is bounded to limit memory usage when the recipient is slow or not responding.
            // When the queue is full, the oldest delivery is discarded to avoid blocking the thread
            final AtomicLong discardedCounter = new AtomicLong();
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    maximumThreadsPerOutboundSubscription,
                    maximumThreadsPerOutboundSubscription,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(maximumQueueSizePerOutboundSubscription),
                    factory,
                    new ThreadPoolExecutor.DiscardOldestPolicy() {
                        @Override
                        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                            if (e.isShutdown()) {
                                // Not caused by a full queue - let the caller handle it
                                throw new RejectedExecutionException("Executor for subscription " + subscriptionId + " has been shut down");
                            }
                            long discarded = discardedCounter.incrementAndGet();
                            if (discarded == 1 || discarded % 100 == 0) {
                                logger.warn("Outbound queue is full for subscription {} - {} deliveries discarded so far", subscriptionId, discarded);
                            }
                            super.rejectedExecution(r, e);
                        }
                    }
            );
            // Release idle threads
            executor.allowCoreThreadTimeOut(true);

            return executor;
        });
    }


    /**
     * Clean up dead ExecutorServices
     * @param subscriptionManager
     */
    private void removeDeadSubscriptionExecutors(ServerSubscriptionManager subscriptionManager) {
        if (threadFactoryMap.isEmpty()) {
            return;
        }
        Set<String> activeSubscriptionIds = subscriptionManager.subscriptions.keySet();
        for (String id : threadFactoryMap.keySet()) {
            if (!activeSubscriptionIds.contains(id)) {
                final ExecutorService service = threadFactoryMap.remove(id);
                // Remaining deliveries are skipped since subscription has been stopped - only a pending TerminateSubscriptionResponse is sent
                if (service != null) {
                    logger.info("Remove executor for subscription {}", id);
                    service.shutdown();
                }
            }
        }
    }

    private int postDataToSubscription(Siri payload, OutboundSubscriptionSetup subscription, boolean logBody) {

        if (serviceDeliveryContainsData(payload)) {
            long t1 = System.currentTimeMillis();
            logger.debug("Posting to subscription {}", subscription.getSubscriptionId());

            Siri transformed = SiriValueTransformer.transform(
                    payload,
                    subscription.getValueAdapters(),
                    false,
                    false);

            if (logBody) {
                try {
                    logger.info("SIRI Payload: {}", SiriXml.toXml(transformed));
                } catch (JAXBException e) {
                    //Ignore
                }
            }

            String siriContentType = "data";
            if (transformed.getServiceDelivery() == null) {
                siriContentType = "heartbeat";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                serializeSiriObject(subscription.getSiriVersion(), transformed, out);
            } catch (Throwable e) {
                logger.info("Failed to serialize SIRI-xml - retrying once, {}", e.toString());
                try {
                    serializeSiriObject(subscription.getSiriVersion(), transformed, out);
                } catch (Throwable ex) {
                    logger.warn("Retry failed to serialize SIRI-xml, {}", ex.toString());
                    throw new RuntimeException(e);
                }
                logger.info("Retry succeeded to serialize SIRI-xml");
            }

            HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create(subscription.getAddress()))
                    .header("subscriptionId", subscription.getSubscriptionId())
                    .header("Content-Type", "application/xml")
                    .timeout(Duration.ofMillis(requestTimeoutMillis))
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(out.toByteArray())))
                    .build();
            int responseCode;
            try {
                responseCode = httpClient.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (Exception e) {
                logger.info("Failed to post {} to subscription {} - retrying, {}", siriContentType, subscription, e.toString());
                // Retry once
                try {
                    responseCode = httpClient.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (Exception ex) {
                    logger.info("Retry failed to post {} to subscription {}, {}", siriContentType, subscription, ex.toString());
                    throw new RuntimeException(e);
                }
            }

            if (responseCode >= 200 && responseCode < 300) {
                // Clearing fail-tracker for all success-responses
                subscriptionManager.clearFailTracker(subscription.getSubscriptionId());
            }

            long elapsed = System.currentTimeMillis() - t1;
            if (elapsed > 100) {
                // Only log "slow" requests
                logger.info("Pushed {} to subscription {} took {} ms, got responseCode {}",
                        siriContentType,
                        subscription.getSubscriptionId(),
                        elapsed,
                        responseCode
                );
            }

            return responseCode;
        }
        return -1;
    }

    private static void serializeSiriObject(SiriValidator.Version version, Siri transformed, ByteArrayOutputStream out) throws JAXBException, IOException, XMLStreamException {
        if (version == SiriValidator.Version.VERSION_2_1) {
            SiriXml.toXml(transformed, null, out);
        } else {
            org.rutebanken.siri20.util.SiriXml.toXml(
                    downgradeSiriVersion(transformed),
                    null,
                    out
            );
        }
    }

    /**
     * Returns false if payload contains an empty ServiceDelivery (i.e. no actual SIRI-data), otherwise it returns false
     * @param payload
     * @return
     */
    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();

            if (SiriHelper.containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                return deliveryStructure.getSituations() != null &&
                        SiriHelper.containsValues(deliveryStructure.getSituations().getPtSituationElements());
            }

            if (SiriHelper.containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                return deliveryStructure.getVehicleActivities() != null &&
                        SiriHelper.containsValues(deliveryStructure.getVehicleActivities());
            }

            if (SiriHelper.containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                return (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
            }
        }
        return true;
    }
}
