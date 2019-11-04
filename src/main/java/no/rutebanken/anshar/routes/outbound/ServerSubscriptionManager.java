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

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.AbstractSubscriptionStructure;
import uk.org.siri.siri20.CheckStatusRequestStructure;
import uk.org.siri.siri20.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri20.SubscriptionRequest;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringSubscriptionStructure;

import javax.annotation.PostConstruct;
import javax.xml.datatype.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.time.temporal.ChronoUnit.MILLIS;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class ServerSubscriptionManager extends CamelRouteManager {

    private final Logger logger = LoggerFactory.getLogger(ServerSubscriptionManager.class);

    @Autowired
    IMap<String, OutboundSubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getLockMap")
    private IMap<String, Instant> lockMap;

    @Autowired
    @Qualifier("getFailTrackerMap")
    private IMap<String, Instant> failTrackerMap;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 60000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    @Value("${anshar.outbound.activemq.topic.prefix}")
    private String activeMqTopicPrefix;

    @Value("${anshar.outbound.activemq.topic.timeToLive.millisec}")
    private int activeMqTopicTimeToLive = 30000;

    @Value("${anshar.outbound.activemq.topic.id.mapping.policy}")
    private OutboundIdMappingPolicy outboundIdMappingPolicy;

    @Value("${anshar.outbound.activemq.topic.enabled}")
    private boolean activeMqTopicEnabled;

    @Autowired
    private SiriHelper siriHelper;

    private OutboundSubscriptionSetup activeMQ_SX;
    private OutboundSubscriptionSetup activeMQ_VM;
    private OutboundSubscriptionSetup activeMQ_ET;
    private OutboundSubscriptionSetup activeMQ_PT;

    @PostConstruct
    private void initializeActiveMqProducers() {
        activeMQ_SX = createActiveMQSubscription(SiriDataType.SITUATION_EXCHANGE, "activemq.internal.subscription.sx");
        activeMQ_VM = createActiveMQSubscription(SiriDataType.VEHICLE_MONITORING, "activemq.internal.subscription.vm");
        activeMQ_ET = createActiveMQSubscription(SiriDataType.ESTIMATED_TIMETABLE, "activemq.internal.subscription.et");
        activeMQ_PT = createActiveMQSubscription(SiriDataType.PRODUCTION_TIMETABLE, "activemq.internal.subscription.pt");
    }

    private OutboundSubscriptionSetup createActiveMQSubscription(SiriDataType type, String subscriptionId) {
        return new OutboundSubscriptionSetup(
                type,
                activeMqTopicPrefix + type.name().toLowerCase(),
                activeMqTopicTimeToLive,
                mappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy),
                subscriptionId);
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
            obj.put("subscriptionRef",""+key);
            obj.put("subscriptionType",""+subscription.getSubscriptionType());
            obj.put("address",""+subscription.getAddress());
            obj.put("heartbeatInterval",""+(subscription.getHeartbeatInterval()/1000) + " s");
            obj.put("datasetId",subscription.getDatasetId()!=null ? subscription.getDatasetId():"");
            obj.put("requestReceived", formatter.format(subscription.getRequestTimestamp()));
            obj.put("initialTerminationTime",formatter.format(subscription.getInitialTerminationTime()));
            obj.put("clientTrackingName",subscription.getClientTrackingName()!=null ? subscription.getClientTrackingName():"");

            stats.add(obj);
        }

        return stats;
    }

    public Siri handleSubscriptionRequest(SubscriptionRequest subscriptionRequest, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName) {

        OutboundSubscriptionSetup subscription = createSubscription(subscriptionRequest, datasetId, outboundIdMappingPolicy, clientTrackingName);

        boolean hasError = false;
        String errorText = null;

        if (subscription.getAddress() == null) {
            hasError = true;
            errorText = errorConsumerAddressMissing;
        } else if (subscription.getInitialTerminationTime() == null || subscription.getInitialTerminationTime().isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            hasError = true;
            errorText = initialTerminationTimePassed;
        }

        if (hasError) {
            Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), false, errorText);
            return subscriptionResponse;
        } else {
            addSubscription(subscription);

            Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), true, null);

            //Send initial ServiceDelivery
            Siri delivery = siriHelper.findInitialDeliveryData(subscription);

            if (delivery != null) {
                logger.info("Sending initial delivery to " + subscription.getAddress());
                pushSiriData(delivery, subscription);
            }
            return subscriptionResponse;
        }
    }



    private OutboundSubscriptionSetup createSubscription(SubscriptionRequest subscriptionRequest, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName) {

        return new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                getSubscriptionType(subscriptionRequest),
                subscriptionRequest.getConsumerAddress() != null ? subscriptionRequest.getConsumerAddress():subscriptionRequest.getAddress(),
                getHeartbeatInterval(subscriptionRequest),
                getChangeBeforeUpdates(subscriptionRequest),
                siriHelper.getFilter(subscriptionRequest),
                mappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy),
                findSubscriptionIdentifier(subscriptionRequest),
                subscriptionRequest.getRequestorRef().getValue(),
                findInitialTerminationTime(subscriptionRequest),
                datasetId,
                clientTrackingName
                );
    }

    private long getHeartbeatInterval(SubscriptionRequest subscriptionRequest) {
        long heartbeatInterval = 0;
        if (subscriptionRequest.getSubscriptionContext() != null &&
                subscriptionRequest.getSubscriptionContext().getHeartbeatInterval() != null) {
            Duration interval = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();
            heartbeatInterval = interval.getTimeInMillis(new Date(0));
        }
        return Math.max(heartbeatInterval, minimumHeartbeatInterval);
    }

    private SiriDataType getSubscriptionType(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return SiriDataType.SITUATION_EXCHANGE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return SiriDataType.VEHICLE_MONITORING;
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return SiriDataType.ESTIMATED_TIMETABLE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getProductionTimetableSubscriptionRequests())) {
            return SiriDataType.PRODUCTION_TIMETABLE;
        }
        return null;
    }

    private long getChangeBeforeUpdates(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getProductionTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        }
        return 0;
    }

    private long getMilliSeconds(Duration changeBeforeUpdates) {
        if (changeBeforeUpdates != null) {
            return changeBeforeUpdates.getSeconds()*1000;
        }
        return 0;
    }

    private void addSubscription(OutboundSubscriptionSetup subscription) {
        subscriptions.put(subscription.getSubscriptionId(), subscription);
    }

    private OutboundSubscriptionSetup removeSubscription(String subscriptionId) {
        logger.info("Removing subscription {}", subscriptionId);
        failTrackerMap.delete(subscriptionId);
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
        }
        return null;
    }

    public void terminateSubscription(String subscriptionRef) {
        OutboundSubscriptionSetup subscriptionRequest = removeSubscription(subscriptionRef);

        if (subscriptionRequest != null) {
            Siri terminateSubscriptionResponse = siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
            logger.info("Sending TerminateSubscriptionResponse to {}", subscriptionRequest.getAddress());

            pushSiriData(terminateSubscriptionResponse, subscriptionRequest);
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        return siriObjectFactory.createCheckStatusResponse();
    }


    public void pushUpdatesAsync(SiriDataType datatype, List updates, String datasetId) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        switch (datatype) {
            case ESTIMATED_TIMETABLE:
                executorService.submit(() -> pushUpdatedEstimatedTimetables(updates, datasetId));
                break;
            case SITUATION_EXCHANGE:
                executorService.submit(() -> pushUpdatedSituations(updates, datasetId));
                break;
            case VEHICLE_MONITORING:
                executorService.submit(() -> pushUpdatedVehicleActivities(updates, datasetId));
                break;
        }
    }

    private void pushUpdatedVehicleActivities(List<VehicleActivityStructure> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_VM);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        ( subscriptionRequest.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->

                        pushSiriData(delivery, subscription)
        );

    }


    private void pushUpdatedSituations(List<PtSituationElement> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createSXServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_SX);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->

                        pushSiriData(delivery, subscription)
        );
    }

    private void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        Siri delivery = siriObjectFactory.createETServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_ET);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription)
        );
    }

    public void pushFailedForSubscription(String subscriptionId) {
        OutboundSubscriptionSetup outboundSubscriptionSetup = subscriptions.get(subscriptionId);
        if (outboundSubscriptionSetup != null) {

            //Grace-period is set to minimum 5 minutes
            long gracePeriod = Math.max(3*outboundSubscriptionSetup.getHeartbeatInterval(), 5*60*1000);

            Instant firstFail = failTrackerMap.getOrDefault(subscriptionId, Instant.now());

            long terminationTime = firstFail.until(Instant.now(), MILLIS);
            if (terminationTime > gracePeriod) {
                logger.info("Cancelling outbound subscription {} that has failed for {}s.", subscriptionId, terminationTime/1000);
                removeSubscription(subscriptionId);
            } else {
                logger.info("Outbound subscription {} has not responded for {}s, will be cancelled after {}s.", subscriptionId, terminationTime/1000, gracePeriod/1000);
                failTrackerMap.set(subscriptionId, firstFail);
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
