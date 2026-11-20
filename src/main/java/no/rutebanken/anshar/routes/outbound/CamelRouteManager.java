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

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
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
    private static final Logger logger = LoggerFactory.getLogger(CamelRouteManager.class);

    private static final String MDC_BREADCRUMB_ID = "camel.breadcrumbId";

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

    /** Lowest upload rate a recipient is expected to manage - a large body gets time to upload on top of the request timeout */
    @Value("${anshar.outbound.timeout.min.throughput.mbit.per.second:25}")
    private int minThroughputMbitPerSecond;

    @Value("${anshar.outbound.timeout.max.seconds:30}")
    private int maxRequestTimeoutSeconds;

    @Value("${anshar.outbound.initialdelivery.resend.backoff.initial.seconds:60}")
    private int resendInitialBackoffSeconds;

    @Value("${anshar.outbound.initialdelivery.resend.backoff.max.seconds:1800}")
    private int resendMaxBackoffSeconds;

    @Value("${anshar.outbound.executor.cleanup.interval.seconds:60}")
    private int executorCleanupIntervalSeconds;

    @Value("${anshar.outbound.circuitbreaker.failure.threshold:5}")
    private int circuitBreakerFailureThreshold;

    @Value("${anshar.outbound.circuitbreaker.backoff.initial.seconds:10}")
    private int circuitBreakerInitialBackoffSeconds;

    @Value("${anshar.outbound.circuitbreaker.backoff.max.seconds:60}")
    private int circuitBreakerMaxBackoffSeconds;

    @Value("${anshar.outbound.probe.timeout.millis:3000}")
    private int probeTimeoutMillis;

    private Map<String, ExecutorService> threadFactoryMap = new ConcurrentHashMap<>();

    private final Map<String, OutboundCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    private ScheduledExecutorService executorCleanupScheduler;

    private volatile boolean shuttingDown = false;

    private static final long CONNECT_TIMEOUT_MILLIS = 2000;

    /**
     * A post that times out is retried at once only up to this size - e.g. a heartbeat or a few changes during a short
     * stall at the recipient. A larger body, like a part of a full dataset, is not uploaded again to a recipient too
     * slow to take it.
     */
    static final long MAX_RETRIED_TIMEOUT_BODY_BYTES = 1024L * 1024;

    /** Time on top of the longest post before a probe that has not completed is abandoned */
    private static final long PROBE_MARGIN_MILLIS = 5000;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
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
        pushSiriData(payload, subscriptionRequest, logBody, requireActiveSubscription, false, null);
    }

    /**
     * Pushes all current data to a subscription - always split into deliveries of at most the maximum size, also for a
     * subscription to a datasetId, whose updates are otherwise sent unsplit. A full dataset can be too large to send
     * in one post.
     */
    void pushInitialDelivery(Siri payload, OutboundSubscriptionSetup subscriptionRequest) {
        pushSiriData(payload, subscriptionRequest, false, true, true, null);
    }

    /** Told how a push went */
    @FunctionalInterface
    interface DeliveryListener {
        /**
         * Not called if the push was cancelled, e.g. the subscription was terminated or re-created while it was
         * queued, or anshar is shutting down.
         *
         * @param outcome {@link Outcome#DELIVERED}, {@link Outcome#REJECTED}, {@link Outcome#SKIPPED} or {@link Outcome#FAILED}
         */
        void completed(Outcome outcome);

        /** A post of the push has completed, whatever its outcome - more may follow */
        default void posted() {
        }
    }

    private void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody,
                              boolean requireActiveSubscription, boolean initialDelivery, DeliveryListener listener) {
        final String subscriptionId = subscriptionRequest.getSubscriptionId();
        final PushTask pushTask = new PushTask(payload, subscriptionRequest, logBody, requireActiveSubscription,
                initialDelivery, listener, MDC.get(MDC_BREADCRUMB_ID));
        if (subscriptionRequest.getAddress() == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            pushTask.dropped();
            return;
        }

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
                if (!requireActiveSubscription) {
                    // Subscription is already removed, so the cleanup-job may shut down any new executor - push directly
                    pushTask.run();
                } else {
                    logger.warn("Unable to push data for subscription {} - executor has been shut down", subscriptionId);
                    pushTask.dropped();
                }
            }
        }
    }

    /** A queued task that must be told if it is dropped without running */
    interface DroppableTask extends Runnable {
        void dropped();
    }

    /**
     * As {@link ThreadPoolExecutor.DiscardOldestPolicy}: the oldest queued task makes room for the new one - but a
     * dropped push is told, so e.g. a resend of all data waiting for its result is retried instead of being forgotten.
     */
    static void discardOldest(Runnable task, ThreadPoolExecutor executor) {
        Runnable oldest = executor.getQueue().poll();
        if (oldest instanceof DroppableTask dropped) {
            dropped.dropped();
        }
        executor.execute(task);
    }

    /** The result of a push - ordered from best to worst, a push with several posts gets the worst */
    enum Outcome {
        /** The recipient took every post (2xx) */
        DELIVERED,
        /** The recipient rejected the body of a post, and would reject it again (400, 413, 415 or 422) */
        REJECTED,
        /** A post was skipped by the circuit breaker - not sent to the recipient */
        SKIPPED,
        /** A post failed (any other response code, or an error), or the push never ran */
        FAILED,
        /** Dropped before posting, e.g. the subscription was terminated or re-created while it was queued */
        CANCELLED;

        static Outcome of(int responseCode) {
            if (responseCode < 300) {
                return DELIVERED;
            }
            return switch (responseCode) {
                // Bad request, too large, unsupported media type or unprocessable - the same body is rejected again
                case 400, 413, 415, 422 -> REJECTED;
                // E.g. server error, throttled, or unauthorized or not found during a deploy - may succeed later
                default -> FAILED;
            };
        }

        Outcome worst(Outcome other) {
            return compareTo(other) >= 0 ? this : other;
        }
    }

    /**
     * Posts a payload to a subscription, split into deliveries if needed, on the subscription's executor. Each post
     * goes through the circuit breaker of the subscription. Not thread-safe - runs once, on one thread.
     */
    private final class PushTask implements DroppableTask {
        private final Siri payload;
        private final OutboundSubscriptionSetup subscriptionRequest;
        private final String subscriptionId;
        private final boolean logBody;
        private final boolean requireActiveSubscription;
        /** All current data - split also for a subscription to a datasetId */
        private final boolean initialDelivery;
        private final DeliveryListener listener;
        private final String breadcrumbId;

        private OutboundCircuitBreaker circuitBreaker;
        // Set while posting - the result must be recorded in the circuit breaker
        private OutboundCircuitBreaker.Permit permit;

        PushTask(Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody,
                 boolean requireActiveSubscription, boolean initialDelivery, DeliveryListener listener, String breadcrumbId) {
            this.payload = payload;
            this.subscriptionRequest = subscriptionRequest;
            this.subscriptionId = subscriptionRequest.getSubscriptionId();
            this.logBody = logBody;
            this.requireActiveSubscription = requireActiveSubscription;
            this.initialDelivery = initialDelivery;
            this.listener = listener;
            this.breadcrumbId = breadcrumbId;
        }

        @Override
        public void run() {
            // Kept if anything is thrown - also an Error, e.g. out of memory building a large delivery
            Outcome outcome = Outcome.FAILED;
            try {
                MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
                MDC.put("subscriptionId", subscriptionId);
                outcome = push();
            } catch (Exception e) {
                if (shuttingDown) {
                    // Interrupted by shutdown - not caused by the subscriber
                    outcome = Outcome.CANCELLED;
                } else {
                    handleFailure(e);
                }
            } finally {
                if (permit != null) {
                    // Not handled above, e.g. an Error was thrown - must be recorded to not block the next probe
                    recordFailure(circuitBreaker, permit, subscriptionId);
                    permit = null;
                    outcome = Outcome.FAILED;
                }
                report(outcome);
                MDC.remove(MDC_BREADCRUMB_ID);
                MDC.remove("subscriptionId");
            }
        }

        /**
         * @return whether the recipient took every post, or that the push was dropped before posting
         */
        private Outcome push() {
            if (requireActiveSubscription && !subscriptionManager.subscriptions.containsKey(subscriptionId)) {
                // Short circuit if subscription has been terminated while waiting
                return Outcome.CANCELLED;
            }

            Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());
            metricsService.countOutgoingData(filteredPayload, SubscriptionSetup.SubscriptionMode.SUBSCRIBE);

            // Updates to a subscription to a datasetId are sent unsplit, but a full dataset may be too large for one post
            boolean unsplit = subscriptionRequest.getDatasetId() != null && !initialDelivery;
            int deliverySize = unsplit ? Integer.MAX_VALUE : maximumSizePerDelivery;
            List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize);

            circuitBreaker = circuitBreaker();
            if (circuitBreaker == null) {
                // Queued for a previous version of the subscription - the new version has received its own initial delivery
                return Outcome.CANCELLED;
            }

            if (splitSiri.size() > 1) {
                logger.info("Object split into {} deliveries for subscription {}.", splitSiri.size(), subscriptionRequest);
            }

            Outcome outcome = Outcome.DELIVERED;
            for (Siri siri : splitSiri) {
                // Nothing to send - not counted as a post to the subscription
                if (serviceDeliveryContainsData(siri)) {
                    outcome = outcome.worst(post(siri));
                    reportPosted();
                }
            }
            return outcome;
        }

        /**
         * @return the circuit breaker to post through, or null if the push was queued for a previous version of the
         * subscription and must be dropped
         */
        private OutboundCircuitBreaker circuitBreaker() {
            OutboundCircuitBreaker current = getCircuitBreaker(subscriptionRequest);
            if (!current.isNewerThan(subscriptionRequest)) {
                return current;
            }
            if (requireActiveSubscription) {
                return null;
            }
            // E.g. TerminateSubscriptionResponse - posted without affecting the circuit breaker of the new version
            return new OutboundCircuitBreaker(null, Integer.MAX_VALUE, 0, 0, 0);
        }

        private Outcome post(Siri siri) {
            permit = circuitBreaker.allowRequest(System.currentTimeMillis());
            if (!permit.isAllowed()) {
                // Recipient keeps failing - skipped until the next probe
                permit = null;
                return Outcome.SKIPPED;
            }
            addSubscriptionRefToServiceDelivery(siri, subscriptionId);
            int responseCode = postDataToSubscription(siri, subscriptionRequest, logBody);

            metricsService.markPostToSubscription(subscriptionRequest.getSubscriptionType(),
                    SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                    subscriptionId,
                    responseCode);

            if (responseCode > 299) {
                logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, responseCode);
                subscriptionManager.pushFailedForSubscription(subscriptionId);
            }
            if (responseCode < 500) {
                // Released first - a resend of all data may be built below, and an error there must not count as a
                // failed post
                permit = null;
                // Recipient is responding - also when it rejects the delivery with 4xx
                recordSuccess(circuitBreaker, subscriptionRequest, responseCode < 300);
            } else {
                recordFailure(circuitBreaker, permit, subscriptionId);
                permit = null;
            }
            return Outcome.of(responseCode);
        }

        private void handleFailure(Exception e) {
            logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, e.toString());

            int statusCode = -1;
            if (e.getCause() instanceof SocketException) {
                logger.info("Recipient is unreachable - ignoring");
            } else {
                String msg = e.getMessage();
                if (e.getCause() != null) {
                    msg = e.getCause().getMessage();
                    if (e.getCause() instanceof HttpOperationFailedException failed) {
                        statusCode = failed.getStatusCode();
                    }
                }
                logger.info("Exception caught when pushing SIRI-data: {}", msg);
            }
            subscriptionManager.pushFailedForSubscription(subscriptionId);
            if (permit != null) {
                // Only failed posts count - not errors before posting
                recordFailure(circuitBreaker, permit, subscriptionId);
                permit = null;
            }

            metricsService.markPostToSubscription(subscriptionRequest.getSubscriptionType(),
                    SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                    subscriptionId,
                    statusCode);
        }

        /** The push will never run, e.g. it was dropped from a full queue */
        @Override
        public void dropped() {
            report(Outcome.FAILED);
        }

        private void reportPosted() {
            if (listener == null) {
                return;
            }
            try {
                listener.posted();
            } catch (RuntimeException e) {
                logger.warn("Failed to handle a post to subscription {}", subscriptionId, e);
            }
        }

        private void report(Outcome outcome) {
            if (listener == null || outcome == Outcome.CANCELLED) {
                return;
            }
            try {
                listener.completed(outcome);
            } catch (RuntimeException e) {
                logger.warn("Failed to handle the result of a push to subscription {}", subscriptionId, e);
            }
        }
    }

    /**
     * Returns the circuit breaker for the subscription - a new one is created if the subscription has been re-created.
     * Deliveries still queued for a previous version of the subscription get the current circuit breaker, and must check
     * {@link OutboundCircuitBreaker#isNewerThan}.
     */
    private OutboundCircuitBreaker getCircuitBreaker(OutboundSubscriptionSetup subscriptionRequest) {
        return circuitBreakers.compute(subscriptionRequest.getSubscriptionId(), (subscriptionId, existing) ->
                existing != null && !existing.isReplacedBy(subscriptionRequest) ?
                        existing :
                        new OutboundCircuitBreaker(subscriptionRequest.getRequestTimestamp(),
                                circuitBreakerFailureThreshold,
                                circuitBreakerInitialBackoffSeconds * 1000L,
                                circuitBreakerMaxBackoffSeconds * 1000L,
                                maxPostDurationMillis() + PROBE_MARGIN_MILLIS,
                                new InitialDeliveryResend(
                                        resendInitialBackoffSeconds * 1000L,
                                        resendMaxBackoffSeconds * 1000L,
                                        lostResendMillis()))
        );
    }

    /**
     * Last resort for a resend of all data that never reports back - pushes that are dropped or never run are reported
     * as failed. A lost resend waits no longer than a failed one at most does, but at least as long as one post can take.
     */
    private long lostResendMillis() {
        return Math.max(resendMaxBackoffSeconds * 1000L, maxPostDurationMillis());
    }

    /** Longest a post can take: it may be retried once, each attempt with connect- and request-timeout */
    private long maxPostDurationMillis() {
        long longestRequestTimeoutMillis = Math.max(requestTimeoutMillis, maxRequestTimeoutSeconds * 1000L);
        return 2L * (CONNECT_TIMEOUT_MILLIS + longestRequestTimeoutMillis);
    }

    private void recordFailure(OutboundCircuitBreaker circuitBreaker, OutboundCircuitBreaker.Permit permit, String subscriptionId) {
        if (circuitBreaker.recordFailure(System.currentTimeMillis(), permit)) {
            logger.warn("Subscription {} has failed {} consecutive times - pausing deliveries for {}s",
                    subscriptionId, circuitBreaker.getConsecutiveFailures(), circuitBreaker.getBackoffMillis() / 1000);
        }
    }

    /**
     * @param delivered whether the recipient took the delivery - a resend of all data is only started then, not when
     *                  it responds with e.g. 413 or 429
     */
    private void recordSuccess(OutboundCircuitBreaker circuitBreaker, OutboundSubscriptionSetup subscriptionRequest, boolean delivered) {
        long skippedDeliveries = circuitBreaker.recordSuccess();
        if (skippedDeliveries >= 0) {
            logger.info("Subscription {} is responding again - resuming deliveries, {} were skipped",
                    subscriptionRequest.getSubscriptionId(), skippedDeliveries);
            circuitBreaker.initialDeliveryResend().request();
        }
        if (!delivered) {
            return;
        }
        // Also started here when a resend that failed earlier has waited out its backoff
        InitialDeliveryResend resend = circuitBreaker.initialDeliveryResend();
        long attempt = resend.start(System.currentTimeMillis());
        if (attempt != InitialDeliveryResend.NOT_STARTED) {
            try {
                resendInitialDelivery(subscriptionRequest, resend, attempt);
            } catch (RuntimeException e) {
                // E.g. building the data failed - the delivery that got through is still a success
                resendFailed(subscriptionRequest, resend, attempt, e);
            }
        }
    }

    /**
     * Deliveries contain changes only, so the recipient is outdated after skipped (or failed) deliveries - resend all
     * current data. A resend that fails is retried after a backoff, see {@link InitialDeliveryResend}.
     */
    private void resendInitialDelivery(OutboundSubscriptionSetup subscriptionRequest, InitialDeliveryResend resend, long attempt) {
        if (!subscriptionManager.subscriptions.containsKey(subscriptionRequest.getSubscriptionId())) {
            resend.succeeded(attempt);
            return;
        }
        Siri delivery = siriHelper.findInitialDeliveryData(subscriptionRequest);
        if (delivery == null) {
            resend.succeeded(attempt);
            return;
        }
        logger.info("Resending initial delivery to {}", subscriptionRequest.getAddress());
        pushSiriData(delivery, subscriptionRequest, false, true, true, new DeliveryListener() {
            @Override
            public void posted() {
                // A resend split into many posts may take long - it is only lost if it stops making progress
                resend.progressed(attempt, System.currentTimeMillis());
            }

            @Override
            public void completed(Outcome outcome) {
                resendCompleted(subscriptionRequest, resend, attempt, outcome);
            }
        });
    }

    private static void resendCompleted(OutboundSubscriptionSetup subscriptionRequest, InitialDeliveryResend resend,
                                        long attempt, Outcome outcome) {
        switch (outcome) {
                case DELIVERED -> {
                    resend.succeeded(attempt);
                    logger.info("Resent initial delivery to {}", subscriptionRequest.getAddress());
                }
                case REJECTED -> {
                    resend.succeeded(attempt);
                    logger.warn("Recipient {} rejected the resent initial delivery - not resent again", subscriptionRequest.getAddress());
                }
                case SKIPPED -> {
                    // Not sent - started again when the circuit closes, without adding to the backoff
                    resend.skipped(attempt);
                    logger.info("Resending initial delivery to {} was skipped by the circuit breaker", subscriptionRequest.getAddress());
                }
                default -> resendFailed(subscriptionRequest, resend, attempt, null);
        }
    }

    private static void resendFailed(OutboundSubscriptionSetup subscriptionRequest, InitialDeliveryResend resend, long attempt, Exception cause) {
        long backoffMillis = resend.failed(attempt, System.currentTimeMillis());
        if (backoffMillis >= 0) {
            logger.warn("Resending initial delivery to {} failed - trying again in {}s at the earliest",
                    subscriptionRequest.getAddress(), backoffMillis / 1000, cause);
        }
    }

    /**
     * Checks that the consumer address can be reached, i.e. that connection and TLS-handshake succeeds. Any HTTP response is accepted.
     * Uses the same HttpClient as the actual deliveries to get the same proxy- and TLS-settings.
     *
     * @return null if the address is reachable, otherwise a description of the error
     */
    String probeConsumerAddress(String address) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(address))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofMillis(probeTimeoutMillis))
                    .build();
            httpClient.send(head, HttpResponse.BodyHandlers.discarding());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e.toString();
        } catch (Exception e) {
            return describeError(e);
        }
    }

    /**
     * Describes the whole cause chain - the root cause alone may not say what failed,
     * i.e. a refused connection on Linux is a ConnectException caused by a ClosedChannelException
     */
    static String describeError(Throwable e) {
        StringJoiner description = new StringJoiner(" <- ");
        for (Throwable t = e; t != null; t = t.getCause()) {
            // Wrappers created from their cause only repeat it
            if (t.getCause() == null || t.getMessage() == null || !t.getMessage().equals(t.getCause().toString())) {
                description.add(t.toString());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return description.toString();
    }

    /**
     * Errors that will not go away by retrying immediately - i.e. an invalid certificate.
     * Other handshake-errors, like "Remote host terminated the handshake", may be transient.
     */
    static boolean isNonTransientError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof CertificateException || t instanceof CertPathValidatorException || t instanceof SSLPeerUnverifiedException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * Whether a failed post is retried at once: not if the retry would fail the same way, nor if it would upload a
     * large body again to a recipient that did not take it in time
     */
    static boolean isRetriedAtOnce(Throwable e, long bodyBytes) {
        if (isNonTransientError(e)) {
            return false;
        }
        return !isRequestTimeout(e) || bodyBytes <= MAX_RETRIED_TIMEOUT_BODY_BYTES;
    }

    /** The recipient did not respond in time after connecting - not a connect timeout, where nothing was sent */
    static boolean isRequestTimeout(Throwable e) {
        return e instanceof HttpTimeoutException && !(e instanceof HttpConnectTimeoutException);
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
                    (r, e) -> {
                        if (e.isShutdown()) {
                            // Not caused by a full queue - let the caller handle it
                            throw new RejectedExecutionException("Executor for subscription " + subscriptionId + " has been shut down");
                        }
                        long discarded = discardedCounter.incrementAndGet();
                        if (discarded == 1 || discarded % 100 == 0) {
                            logger.warn("Outbound queue is full for subscription {} - {} deliveries discarded so far", subscriptionId, discarded);
                        }
                        discardOldest(r, e);
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
        if (threadFactoryMap.isEmpty() && circuitBreakers.isEmpty()) {
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
        circuitBreakers.keySet().removeIf(id -> !activeSubscriptionIds.contains(id));
    }

    private int postDataToSubscription(Siri payload, OutboundSubscriptionSetup subscription, boolean logBody) {

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
        ChunkedBody body = serializeWithRetry(out -> serializeSiriObject(subscription.getSiriVersion(), transformed, out));

        HttpRequest post = HttpRequest.newBuilder()
                .uri(URI.create(subscription.getAddress()))
                .header("subscriptionId", subscription.getSubscriptionId())
                .header("Content-Type", "application/xml")
                .timeout(requestTimeout(body.size(), requestTimeoutMillis, minThroughputMbitPerSecond, maxRequestTimeoutSeconds))
                .POST(body.publisher())
                .build();
        int responseCode;
        try {
            responseCode = httpClient.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            // E.g. shutting down - not retried
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            if (!isRetriedAtOnce(e, body.size())) {
                throw new RuntimeException(e);
            }
            logger.info("Failed to post {} ({} bytes) to subscription {} - retrying, {}", siriContentType, body.size(), subscription, e.toString());
            // Retry once
            try {
                responseCode = httpClient.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                e.addSuppressed(ex);
                throw new RuntimeException(e);
            } catch (Exception ex) {
                logger.info("Retry failed to post {} ({} bytes) to subscription {}, {}", siriContentType, body.size(), subscription, ex.toString());
                e.addSuppressed(ex);
                throw new RuntimeException(e);
            }
        }

        if (responseCode >= 200 && responseCode < 300 && transformed.getServiceDelivery() != null) {
            // Only a delivered ServiceDelivery clears the fail-tracker. A recipient that accepts heartbeats but keeps
            // rejecting data must still be cancelled after the grace period.
            subscriptionManager.clearFailTracker(subscription.getSubscriptionId());
        }

        long elapsed = System.currentTimeMillis() - t1;
        if (elapsed > 100) {
            // Only log "slow" requests
            logger.info("Pushed {} ({} bytes) to subscription {} took {} ms, got responseCode {}",
                    siriContentType,
                    body.size(),
                    subscription.getSubscriptionId(),
                    elapsed,
                    responseCode
            );
        }

        return responseCode;
    }

    /**
     * The time a post may take until the response arrives, which includes uploading the body: the request timeout,
     * plus the time to upload the body at the lowest expected throughput, up to a maximum. A delivery of 6 MB, e.g. one
     * part of a full dataset, needs about 2 s at 25 Mbit/s, while a few changes need no more than the request timeout.
     */
    static Duration requestTimeout(long bodyBytes, long requestTimeoutMillis, long minThroughputMbitPerSecond, long maxSeconds) {
        if (minThroughputMbitPerSecond <= 0) {
            // No upload time added
            return Duration.ofMillis(requestTimeoutMillis);
        }
        long uploadMillis = bodyBytes * 8 * 1000 / (minThroughputMbitPerSecond * 1_000_000);
        long maxMillis = Math.max(requestTimeoutMillis, maxSeconds * 1000);
        return Duration.ofMillis(Math.min(requestTimeoutMillis + uploadMillis, maxMillis));
    }

    /** Writes a push body */
    @FunctionalInterface
    interface BodyWriter {
        void write(OutputStream out) throws JAXBException, IOException, XMLStreamException;
    }

    /**
     * The body written by {@code writer}, retried once if it fails. Held in chunks rather than one growing array, and
     * sent as is for each attempt, so a large delivery allocates no humongous objects.
     */
    static ChunkedBody serializeWithRetry(BodyWriter writer) {
        ChunkedBody body = new ChunkedBody();
        try {
            writer.write(body);
        } catch (Exception e) {
            // Errors such as OutOfMemoryError are not retried
            logger.info("Failed to serialize SIRI-xml - retrying once, {}", e.toString());
            // Whatever the failed attempt wrote is incomplete
            body.reset();
            try {
                writer.write(body);
            } catch (Exception ex) {
                logger.warn("Retry failed to serialize SIRI-xml, {}", ex.toString());
                throw new RuntimeException(e);
            }
            logger.info("Retry succeeded to serialize SIRI-xml");
        }
        return body;
    }

    private static void serializeSiriObject(SiriValidator.Version version, Siri transformed, OutputStream out) throws JAXBException, IOException, XMLStreamException {
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
