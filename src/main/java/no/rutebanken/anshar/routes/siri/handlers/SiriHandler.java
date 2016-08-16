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
            } else if (incoming.getSubscriptionResponse() != null) {
                SubscriptionResponseStructure subscriptionResponse = incoming.getSubscriptionResponse();
                subscriptionResponse.getResponseStatuses().forEach(responseStatus ->
                                SubscriptionManager.activatePendingSubscription(subscriptionId)
                );

            } else if (incoming.getTerminateSubscriptionResponse() != null) {
                TerminateSubscriptionResponseStructure terminateSubscriptionResponse = incoming.getTerminateSubscriptionResponse();
                boolean removed = SubscriptionManager.removeSubscription(subscriptionId);

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
                        logger.info("Subscription [{}]: Got SX-delivery", subscriptionSetup);
                        situationExchangeDeliveries.forEach(sx ->
                                        sx.getSituations().getPtSituationElements().forEach(ptSx -> Situations.add(ptSx))
                        );
                        logger.trace("Active SX-elements: {}", Situations.getAll().size());
                    }
                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
                        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
                        logger.info("Subscription [{}]: Got VM-delivery", subscriptionSetup);
                        vehicleMonitoringDeliveries.forEach(vm ->
                                        vm.getVehicleActivities().forEach(activity -> Vehicles.add(activity))
                        );
                        logger.trace("Active VM-elements: {}", Vehicles.getAll().size());
                    }
                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
                        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                        logger.info("Subscription [{}]: Got ET-delivery", subscriptionSetup);
                        estimatedTimetableDeliveries.forEach(et ->
                                        Journeys.add(et)
                        );
                        logger.trace("Active ET-elements: {}", Journeys.getAll().size());
                    }
                    if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
                        List<ProductionTimetableDeliveryStructure> productionTimetableDeliveries = incoming.getServiceDelivery().getProductionTimetableDeliveries();
                        logger.info("Subscription [{}]: Got PT-delivery", subscriptionSetup);
                        productionTimetableDeliveries.forEach(pt ->
                                        ProductionTimetables.add(pt)
                        );
                        logger.trace("Active ET-elements: {}", Journeys.getAll().size());
                    }
                } else {
                    logger.info("ServiceDelivery for invalid subscriptionId [{}] ignored.", subscriptionId);
                }
                logger.trace("Current valid situations: {}", Situations.getAll().size());
            }

            return incoming;
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

}
