package no.rutebanken.anshar.routes.siri.handlers;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class SiriHandler {

    private static Logger logger = LoggerFactory.getLogger(SiriHandler.class);

    private ServerSubscriptionManager serverSubscriptionManager;

    public SiriHandler() {
        serverSubscriptionManager = new ServerSubscriptionManager();
    }

    public Siri handleIncomingSiri(String subscriptionId, String xml) {
        try {
            if (subscriptionId != null) {
                return processSiriClientRequest(subscriptionId, xml);
            } else {
                return processSiriServerRequest(xml);
            }
        } catch (JAXBException e) {
            logger.warn("Caught exception when parsing incoming XML", e);
        }
        return null;
    }

    /**
     * Handling incoming requests from external clients
     *
     * @param xml
     * @throws JAXBException
     */
    private Siri processSiriServerRequest(String xml) throws JAXBException {
        Siri incoming = SiriValueTransformer.parseXml(xml);

        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest...");
            serverSubscriptionManager.handleSubscriptionRequest(incoming.getSubscriptionRequest());

        } else if (incoming.getTerminateSubscriptionRequest() != null) {
            logger.info("Handling terminateSubscriptionrequest...");
            serverSubscriptionManager.terminateSubscription(incoming.getTerminateSubscriptionRequest());

        } else if (incoming.getCheckStatusRequest() != null) {
            logger.info("Handling checkStatusRequest...");
            serverSubscriptionManager.handleCheckStatusRequest(incoming.getCheckStatusRequest());
        } else if (incoming.getServiceRequest() != null) {
            logger.info("Handling serviceRequest...");
            ServiceRequest serviceRequest = incoming.getServiceRequest();

            if (serviceRequest.getSituationExchangeRequests() != null) {
                return SiriObjectFactory.createSXServiceDelivery(Situations.getAll());
            } else if (serviceRequest.getVehicleMonitoringRequests() != null) {
                return SiriObjectFactory.createVMServiceDelivery(VehicleActivities.getAll());
            } else if (serviceRequest.getEstimatedTimetableRequests() != null) {
                return SiriObjectFactory.createETServiceDelivery(EstimatedTimetables.getAll());
            }
        }

        return null;
    }

    /**
     * Handling incoming requests from external servers
     *
     * @param subscriptionId
     * @param xml
     * @return
     * @throws JAXBException
     */
    private Siri processSiriClientRequest(String subscriptionId, String xml) throws JAXBException {
        SubscriptionSetup subscriptionSetup = SubscriptionManager.get(subscriptionId);

        if (subscriptionSetup != null) {
            Siri incoming = SiriValueTransformer.parseXml(xml, subscriptionSetup.getMappingAdapters());

            if (incoming.getHeartbeatNotification() != null) {
                SubscriptionManager.touchSubscription(subscriptionId);

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

            } else if (incoming.getDataReadyNotification() != null) {
                //Fetched delivery
                DataReadyRequestStructure dataReadyNotification = incoming.getDataReadyNotification();
                //TODO: Implement this?

                //
                DataReadyResponseStructure dataReadyAcknowledgement = new DataReadyResponseStructure();
                dataReadyAcknowledgement.setResponseTimestamp(ZonedDateTime.now());
                dataReadyAcknowledgement.setConsumerRef(dataReadyNotification.getProducerRef());

            } else if (incoming.getServiceDelivery() != null) {
                SubscriptionManager.touchSubscription(subscriptionId);

                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)) {
                    List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
                    logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetup);

                    List<PtSituationElement> addedOrUpdated = new ArrayList<>();
                    situationExchangeDeliveries.forEach(sx ->
                                    sx.getSituations().getPtSituationElements().forEach(ptSx -> {
                                        PtSituationElement situationElement = Situations.add(ptSx, subscriptionSetup.getDatasetId());
                                        if (situationElement != null) {
                                            addedOrUpdated.add(situationElement);
                                        }
                                    })
                    );

                    serverSubscriptionManager.pushUpdatedSituations(addedOrUpdated);

                    logger.info("Active SX-elements: {}, new/updated: {}", Situations.getAll().size(), addedOrUpdated.size());
                }
                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
                    List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
                    logger.info("Got VM-delivery: Subscription [{}]", subscriptionSetup);

                    List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
                    vehicleMonitoringDeliveries.forEach(vm ->
                                    vm.getVehicleActivities().forEach(activity -> {
                                                VehicleActivityStructure addedOrUpdatedActivity = VehicleActivities.add(activity, subscriptionSetup.getDatasetId());
                                                if (addedOrUpdatedActivity != null) {
                                                    addedOrUpdated.add(addedOrUpdatedActivity);
                                                }
                                            }
                                    )
                    );

                    serverSubscriptionManager.pushUpdatedVehicleActivities(addedOrUpdated);

                    logger.info("Active VM-elements: {}, new/updated: {}", VehicleActivities.getAll().size(), addedOrUpdated.size());
                }
                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
                    List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                    logger.info("Got ET-delivery: Subscription [{}]", subscriptionSetup);

                    List<EstimatedTimetableDeliveryStructure> addedOrUpdated = new ArrayList<>();
                    estimatedTimetableDeliveries.forEach(et -> {
                                EstimatedTimetableDeliveryStructure element = EstimatedTimetables.add(et, subscriptionSetup.getDatasetId());
                                if (element != null) {
                                    addedOrUpdated.add(element);
                                }
                            }
                    );
                    serverSubscriptionManager.pushUpdatedEstimatedTimetables(addedOrUpdated);
                    logger.info("Active ET-elements: {}, new/updated: {}", EstimatedTimetables.getAll().size(), addedOrUpdated.size());
                }
                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
                    List<ProductionTimetableDeliveryStructure> productionTimetableDeliveries = incoming.getServiceDelivery().getProductionTimetableDeliveries();
                    logger.info("Got PT-delivery: Subscription [{}]", subscriptionSetup);

                    List<ProductionTimetableDeliveryStructure> addedOrUpdated = new ArrayList<>();
                    productionTimetableDeliveries.forEach(pt -> {
                                ProductionTimetableDeliveryStructure element = ProductionTimetables.add(pt, subscriptionSetup.getDatasetId());
                                if (element != null) {
                                    addedOrUpdated.add(element);
                                }
                            }
                    );
                    serverSubscriptionManager.pushUpdatedProductionTimetables(addedOrUpdated);
                    logger.info("Active ET-elements: {}, new/updated: {}", EstimatedTimetables.getAll().size(), addedOrUpdated.size());
                }
            } else {
                return incoming;
            }
        } else {
            logger.debug("ServiceDelivery for invalid subscriptionId [{}] ignored.", subscriptionId);
        }
        return null;
    }

}
