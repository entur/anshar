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
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import javax.annotation.PostConstruct;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();


    @PostConstruct
    private void initThreadMetrics() {
        metricsService.registerOutboundThreadFactoryMap(threadFactoryMap);
    }

    /**
     * Splits SIRI-data if applicable, and pushes data to external subscription
     * @param payload
     * @param subscriptionRequest
     */
    void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody) {
        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }
        final String breadcrumbId = MDC.get("camel.breadcrumbId");
        ExecutorService executorService = getOrCreateExecutorService(subscriptionRequest);
        executorService.execute(() -> {
            try {
                MDC.put("camel.breadcrumbId", breadcrumbId);
                MDC.put("subscriptionId", subscriptionRequest.getSubscriptionId());
                if (!subscriptionManager.subscriptions.containsKey(subscriptionRequest.getSubscriptionId())) {
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
                    int responseCode = postDataToSubscription(siri, subscriptionRequest, logBody);
                    metricsService.markPostToSubscription(subscriptionRequest.getSubscriptionType(),
                            SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                            subscriptionRequest.getSubscriptionId(),
                            responseCode);
                }
            } catch (Exception e) {
                logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, e.getMessage());

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
                subscriptionManager.pushFailedForSubscription(subscriptionRequest.getSubscriptionId());

                metricsService.markPostToSubscription(subscriptionRequest.getSubscriptionType(),
                        SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                        subscriptionRequest.getSubscriptionId(),
                        statusCode);

                removeDeadSubscriptionExecutors(subscriptionManager);
            } finally {
                MDC.remove("camel.breadcrumbId");
                MDC.remove("subscriptionId");
            }
        });
    }

    Map<String, ExecutorService> threadFactoryMap = new HashMap<>();
    private ExecutorService getOrCreateExecutorService(OutboundSubscriptionSetup subscriptionRequest) {

        final String subscriptionId = subscriptionRequest.getSubscriptionId();
        if (!threadFactoryMap.containsKey(subscriptionId)) {
            ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("outbound-"+subscriptionId).build();

            //Specifying RejectedExecutionHandler as DiscardOldestPolicy to avoid blocking the thread
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    maximumThreadsPerOutboundSubscription,
                    maximumThreadsPerOutboundSubscription,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    factory,
                    new ThreadPoolExecutor.DiscardOldestPolicy()
            );

            threadFactoryMap.put(subscriptionId, executor);
        }

        return threadFactoryMap.get(subscriptionId);
    }


    /**
     * Clean up dead ExecutorServices
     * @param subscriptionManager
     */
    private void removeDeadSubscriptionExecutors(ServerSubscriptionManager subscriptionManager) {
        List<String> idsToRemove = new ArrayList<>();
        for (String id : threadFactoryMap.keySet()) {
            if (!subscriptionManager.subscriptions.containsKey(id)) {
                final ExecutorService service = threadFactoryMap.get(id);
                idsToRemove.add(id);
                // Force shutdown since outbound subscription has been stopped
                if (service != null) {
                    service.shutdownNow();
                }
            }
        }
        if (!idsToRemove.isEmpty()) {
            for (String id : idsToRemove) {
                logger.info("Remove executor for subscription {}", id);
                threadFactoryMap.remove(id);
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

            String siriContentType = "data";
            if (transformed.getServiceDelivery() == null) {
                siriContentType = "heartbeat";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                serializeSiriObject(subscription.getSiriVersion(), transformed, out);
            } catch (Throwable e) {
                logger.info("Failed to serialize SIRI-xml - retrying once, {}", e.getMessage());
                try {
                    serializeSiriObject(subscription.getSiriVersion(), transformed, out);
                } catch (Throwable ex) {
                    logger.warn("Retry failed to serialize SIRI-xml, {}", ex.getMessage());
                    throw new RuntimeException(e);
                }
                logger.info("Retry succeeded to serialize SIRI-xml");
            }

            HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create(subscription.getAddress()))
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(out.toByteArray())))
                    .build();
            int responseCode;
            try {
                responseCode = httpClient.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (Exception e) {
                logger.info("Failed to post {} to subscription {} - retrying, {}", siriContentType, subscription, e.getMessage());
                // Retry once
                try {
                    responseCode = httpClient.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (Exception ex) {
                    logger.info("Retry failed to post {} to subscription {}, {}", siriContentType, subscription, ex.getMessage());
                    throw new RuntimeException(e);
                }
            }

            if (responseCode == 200) {
                subscriptionManager.clearFailTracker(subscription.getSubscriptionId());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Pushed {} to subscription {} took {} ms, got responseCode {}",
                        siriContentType,
                        subscription.getSubscriptionId(),
                        System.currentTimeMillis() - t1,
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
