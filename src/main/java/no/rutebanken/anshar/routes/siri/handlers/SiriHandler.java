package no.rutebanken.anshar.routes.siri.handlers;

import no.rutebanken.anshar.messages.Journeys;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Vehicles;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.time.ZonedDateTime;
import java.util.List;

public class SiriHandler {

    private static Logger logger = LoggerFactory.getLogger(SiriHandler.class);

    public Siri handleIncomingSiri(String subscriptionId, String xml) {
        try {
            Siri incoming = SiriXml.parseXml(xml);

            if (incoming.getHeartbeatNotification() != null) {
                SubscriptionManager.touchSubscription(subscriptionId);

            } else if (incoming.getSubscriptionRequest() != null) {
                logger.info("Ignoring subscriptionrequest...");
            } else if (incoming.getCheckStatusResponse() != null) {
                logger.info("Incoming CheckStatusResponse [{}]", subscriptionId);
                SubscriptionManager.touchSubscription(subscriptionId, incoming.getCheckStatusResponse().getServiceStartedTime());
            } else if (incoming.getSubscriptionResponse() != null) {
                SubscriptionResponseStructure subscriptionResponse = incoming.getSubscriptionResponse();
                subscriptionResponse.getResponseStatuses().forEach(responseStatus ->
                                SubscriptionManager.activatePendingSubscription(subscriptionId)
                );

            } else if (incoming.getTerminateSubscriptionResponse() != null) {
                TerminateSubscriptionResponseStructure terminateSubscriptionResponse = incoming.getTerminateSubscriptionResponse();
                boolean terminated = SubscriptionManager.removeSubscription(subscriptionId);

                logger.info("Subscription [{}]  terminated: {}", subscriptionId, terminated);

            } else if (incoming.getDataReadyNotification() != null ){
                //Fetched delivery
                DataReadyRequestStructure dataReadyNotification = incoming.getDataReadyNotification();
                //TODO: Implement this?

                //
                DataReadyResponseStructure dataReadyAcknowledgement = new DataReadyResponseStructure();
                dataReadyAcknowledgement.setResponseTimestamp(ZonedDateTime.now());
                dataReadyAcknowledgement.setConsumerRef(dataReadyNotification.getProducerRef());

            } else if (incoming.getServiceDelivery() != null) {
                SubscriptionSetup subscriptionSetup = SubscriptionManager.get(subscriptionId);
                if (subscriptionSetup != null) {
                    SubscriptionManager.touchSubscription(subscriptionId);

                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)) {
                        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
                        logger.trace("Got SX-delivery: Subscription [{}]", subscriptionSetup);
                        situationExchangeDeliveries.forEach(sx ->
                                        sx.getSituations().getPtSituationElements().forEach(ptSx -> Situations.add(ptSx, subscriptionSetup.getDatasetId()))
                        );
                        logger.trace("Active SX-elements: {}", Situations.getAll().size());
                    }
                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
                        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
                        logger.trace("Got VM-delivery: Subscription [{}]", subscriptionSetup);
                        vehicleMonitoringDeliveries.forEach(vm ->
                                        vm.getVehicleActivities().forEach(activity -> Vehicles.add(activity, subscriptionSetup.getDatasetId()))
                        );
                        logger.trace("Active VM-elements: {}", Vehicles.getAll().size());
                    }
                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
                        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                        logger.trace("Got ET-delivery: Subscription [{}]", subscriptionSetup);
                        estimatedTimetableDeliveries.forEach(et ->
                                        Journeys.add(et, subscriptionSetup.getDatasetId())
                        );
                        logger.trace("Active ET-elements: {}", Journeys.getAll().size());
                    }
                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
                        List<ProductionTimetableDeliveryStructure> productionTimetableDeliveries = incoming.getServiceDelivery().getProductionTimetableDeliveries();
                        logger.trace("Got PT-delivery: Subscription [{}]", subscriptionSetup);
                        productionTimetableDeliveries.forEach(pt ->
                                        ProductionTimetables.add(pt, subscriptionSetup.getDatasetId())
                        );
                        logger.trace("Active ET-elements: {}", Journeys.getAll().size());
                    }
                } else {
                    logger.debug("ServiceDelivery for invalid subscriptionId [{}] ignored.", subscriptionId);
                }
            }

            return incoming;
        } catch (JAXBException e) {
            logger.warn("Caught exception when parsing incoming XML", e);
        }
        return null;
    }

}
