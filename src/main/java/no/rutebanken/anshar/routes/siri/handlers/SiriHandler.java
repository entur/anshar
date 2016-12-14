package no.rutebanken.anshar.routes.siri.handlers;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.ServiceNotSupportedException;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.MappingAdapterPresets;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class SiriHandler {

    private Logger logger = LoggerFactory.getLogger(SiriHandler.class);

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private ProductionTimetables productionTimetables;

    @Autowired
    private SiriObjectFactory siriObjectFactory;


    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    public SiriHandler() {

    }

    public Siri handleIncomingSiri(String subscriptionId, String xml) {
        return handleIncomingSiri(subscriptionId, xml, null);
    }

    public Siri handleIncomingSiri(String subscriptionId, String xml, String datasetId) {
        try {
            if (subscriptionId != null) {
                return processSiriClientRequest(subscriptionId, xml);
            } else {
                return processSiriServerRequest(xml, datasetId);
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
    private Siri processSiriServerRequest(String xml, String datasetId) throws JAXBException {
        Siri incoming = SiriValueTransformer.parseXml(xml);

        boolean useMappedId = useMappedId(incoming);

        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest...");
            serverSubscriptionManager.handleSubscriptionRequest(incoming.getSubscriptionRequest(), datasetId, useMappedId);

        } else if (incoming.getTerminateSubscriptionRequest() != null) {
            logger.info("Handling terminateSubscriptionrequest...");
            serverSubscriptionManager.terminateSubscription(incoming.getTerminateSubscriptionRequest());

        } else if (incoming.getCheckStatusRequest() != null) {
            logger.info("Handling checkStatusRequest...");
            return serverSubscriptionManager.handleCheckStatusRequest(incoming.getCheckStatusRequest());
        } else if (incoming.getServiceRequest() != null) {
            logger.info("Handling serviceRequest...");
            ServiceRequest serviceRequest = incoming.getServiceRequest();
            String requestorRef = null;

            Siri serviceResponse = null;

            if (serviceRequest.getRequestorRef() != null) {
                requestorRef = serviceRequest.getRequestorRef().getValue();
            }

            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                serviceResponse = siriObjectFactory.createSXServiceDelivery(situations.getAllUpdates(requestorRef, datasetId));
            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {

                Map<Class, Set<String>> filterMap = new HashMap<>();
                for (VehicleMonitoringRequestStructure req : serviceRequest.getVehicleMonitoringRequests()) {
                    LineRef lineRef = req.getLineRef();
                    if (lineRef != null) {
                        Set linerefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class): new HashSet<>();
                        linerefList.add(lineRef.getValue());
                        filterMap.put(LineRef.class, linerefList);
                    }
                    VehicleRef vehicleRef = req.getVehicleRef();
                    if (vehicleRef != null) {
                        Set vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class): new HashSet<>();
                        vehicleRefList.add(vehicleRef.getValue());
                        filterMap.put(VehicleRef.class, vehicleRefList);
                    }
                }
                if (!filterMap.isEmpty()) {
                    //Filter is specified - return data even if they have not changed
                    requestorRef = null;
                }

                Siri siri = siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAllUpdates(requestorRef, datasetId));
                serviceResponse = SiriHelper.filterSiriPayload(siri, filterMap);
            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                serviceResponse = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllUpdates(requestorRef, datasetId));
            } else if (hasValues(serviceRequest.getProductionTimetableRequests())) {
                serviceResponse = siriObjectFactory.createPTServiceDelivery(productionTimetables.getAllUpdates(requestorRef, datasetId));
            }

            if (serviceResponse != null) {
                return SiriValueTransformer.transform(serviceResponse, mappingAdapterPresets.getOutboundAdapters(useMappedId));
            }
        }

        return null;
    }

    private boolean useMappedId(Siri siri) {
        boolean useMappedId = true;
        Extensions extensions = siri.getExtensions();
        if (extensions != null && extensions.getAnies() != null) {
            for (Element element : extensions.getAnies()) {
                if ("IdMapping".equalsIgnoreCase(element.getLocalName())) {
                    String nsrAttr = element.getAttribute("useOriginalId");
                    if (nsrAttr != null) {
                        boolean useOriginalId = Boolean.valueOf(nsrAttr);
                        useMappedId = !useOriginalId;
                    }
                }
            }
        }
        return useMappedId;
    }

    private boolean hasValues(List list) {
        return (list != null && !list.isEmpty());
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
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup != null) {
            Siri incoming = SiriValueTransformer.parseXml(xml, subscriptionSetup.getMappingAdapters());

            if (incoming.getHeartbeatNotification() != null) {
                subscriptionManager.touchSubscription(subscriptionId);
                logger.info("Heartbeat - {}", subscriptionSetup);
            } else if (incoming.getCheckStatusResponse() != null) {
                logger.info("Incoming CheckStatusResponse [{}]", subscriptionId);
                subscriptionManager.touchSubscription(subscriptionId, incoming.getCheckStatusResponse().getServiceStartedTime());
            } else if (incoming.getSubscriptionResponse() != null) {
                SubscriptionResponseStructure subscriptionResponse = incoming.getSubscriptionResponse();
                subscriptionResponse.getResponseStatuses().forEach(responseStatus ->
                                subscriptionManager.activatePendingSubscription(subscriptionId)
                );

            } else if (incoming.getTerminateSubscriptionResponse() != null) {
                TerminateSubscriptionResponseStructure terminateSubscriptionResponse = incoming.getTerminateSubscriptionResponse();
                boolean terminated = subscriptionManager.removeSubscription(subscriptionId);

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
                subscriptionManager.touchSubscription(subscriptionId);

                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)) {
                    List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
                    logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetup);

                    List<PtSituationElement> addedOrUpdated = new ArrayList<>();
                    situationExchangeDeliveries.forEach(sx -> {
                                if (sx.isStatus() != null && !sx.isStatus()) {
                                    logger.info(getErrorContents(sx.getErrorCondition()));
                                } else {
                                    addedOrUpdated.addAll(
                                            situations.addAll(subscriptionSetup.getDatasetId(), sx.getSituations().getPtSituationElements())
                                    );
                                }
                            }
                    );

                    serverSubscriptionManager.pushUpdatedSituations(addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active SX-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
                    List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
                    logger.info("Got VM-delivery: Subscription [{}]", subscriptionSetup);

                    List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
                    vehicleMonitoringDeliveries.forEach(vm -> {
                            if (vm.isStatus() != null && !vm.isStatus()) {
                                logger.info(getErrorContents(vm.getErrorCondition()));
                            } else {
                                addedOrUpdated.addAll(
                                        vehicleActivities.addAll(subscriptionSetup.getDatasetId(), vm.getVehicleActivities())
                                );
                            }
                        }
                    );

                    serverSubscriptionManager.pushUpdatedVehicleActivities(addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active VM-elements: {}, current delivery: {}, {}", vehicleActivities.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
                    List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                    logger.info("Got ET-delivery: Subscription {}", subscriptionSetup);

                    List<EstimatedVehicleJourney> addedOrUpdated = new ArrayList<>();
                    estimatedTimetableDeliveries.forEach(et -> {
                                if (et.isStatus() != null && !et.isStatus()) {
                                    logger.info(getErrorContents(et.getErrorCondition()));
                                } else {
                                    et.getEstimatedJourneyVersionFrames().forEach(versionFrame -> {
                                        addedOrUpdated.addAll(
                                                estimatedTimetables.addAll(subscriptionSetup.getDatasetId(), versionFrame.getEstimatedVehicleJourneies())
                                        );
                                    });
                                }
                            }
                    );
                    serverSubscriptionManager.pushUpdatedEstimatedTimetables(addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active ET-elements: {}, current delivery: {}, {}", estimatedTimetables.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
                    List<ProductionTimetableDeliveryStructure> productionTimetableDeliveries = incoming.getServiceDelivery().getProductionTimetableDeliveries();
                    logger.info("Got PT-delivery: Subscription [{}]", subscriptionSetup);

                    List<ProductionTimetableDeliveryStructure> addedOrUpdated = new ArrayList<>();

                    addedOrUpdated.addAll(
                        productionTimetables.addAll(subscriptionSetup.getDatasetId(), productionTimetableDeliveries)
                    );

                    serverSubscriptionManager.pushUpdatedProductionTimetables(addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active PT-elements: {}, current delivery: {}, {}", productionTimetables.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
            } else {
                throw new RuntimeException(new ServiceNotSupportedException());
            }
        } else {
            logger.debug("ServiceDelivery for invalid subscriptionId [{}] ignored.", subscriptionId);
        }
        return null;
    }

    /**
     * Creates a json-string containing all potential errormessage-values
     *
     * @param errorCondition
     * @return
     */
    private String getErrorContents(ServiceDeliveryErrorConditionElement errorCondition) {
        String errorContents = "";
        if (errorCondition != null) {
            Map<String, String> errorMap = new HashMap<>();
            String accessNotAllowed = getErrorText(errorCondition.getAccessNotAllowedError());
            String allowedResourceUsageExceeded = getErrorText(errorCondition.getAllowedResourceUsageExceededError());
            String beyondDataHorizon = getErrorText(errorCondition.getBeyondDataHorizon());
            String capabilityNotSupportedError = getErrorText(errorCondition.getCapabilityNotSupportedError());
            String endpointDeniedAccessError = getErrorText(errorCondition.getEndpointDeniedAccessError());
            String endpointNotAvailableAccessError = getErrorText(errorCondition.getEndpointNotAvailableAccessError());
            String invalidDataReferencesError = getErrorText(errorCondition.getInvalidDataReferencesError());
            String parametersIgnoredError = getErrorText(errorCondition.getParametersIgnoredError());
            String serviceNotAvailableError = getErrorText(errorCondition.getServiceNotAvailableError());
            String unapprovedKeyAccessError = getErrorText(errorCondition.getUnapprovedKeyAccessError());
            String unknownEndpointError = getErrorText(errorCondition.getUnknownEndpointError());
            String unknownExtensionsError = getErrorText(errorCondition.getUnknownExtensionsError());
            String unknownParticipantError = getErrorText(errorCondition.getUnknownParticipantError());
            String noInfoForTopicError = getErrorText(errorCondition.getNoInfoForTopicError());
            String otherError = getErrorText(errorCondition.getOtherError());

            String description = getDescriptionText(errorCondition.getDescription());

            if (accessNotAllowed != null) {errorMap.put("accessNotAllowed", accessNotAllowed);}
            if (allowedResourceUsageExceeded != null) {errorMap.put("allowedResourceUsageExceeded", allowedResourceUsageExceeded);}
            if (beyondDataHorizon != null) {errorMap.put("beyondDataHorizon", beyondDataHorizon);}
            if (capabilityNotSupportedError != null) {errorMap.put("capabilityNotSupportedError", capabilityNotSupportedError);}
            if (endpointDeniedAccessError != null) {errorMap.put("endpointDeniedAccessError", endpointDeniedAccessError);}
            if (endpointNotAvailableAccessError != null) {errorMap.put("endpointNotAvailableAccessError", endpointNotAvailableAccessError);}
            if (invalidDataReferencesError != null) {errorMap.put("invalidDataReferencesError", invalidDataReferencesError);}
            if (parametersIgnoredError != null) {errorMap.put("parametersIgnoredError", parametersIgnoredError);}
            if (serviceNotAvailableError != null) {errorMap.put("serviceNotAvailableError", serviceNotAvailableError);}
            if (unapprovedKeyAccessError != null) {errorMap.put("unapprovedKeyAccessError", unapprovedKeyAccessError);}
            if (unknownEndpointError != null) {errorMap.put("unknownEndpointError", unknownEndpointError);}
            if (unknownExtensionsError != null) {errorMap.put("unknownExtensionsError", unknownExtensionsError);}
            if (unknownParticipantError != null) {errorMap.put("unknownParticipantError", unknownParticipantError);}
            if (noInfoForTopicError != null) {errorMap.put("noInfoForTopicError", noInfoForTopicError);}
            if (otherError != null) {errorMap.put("otherError", otherError);}
            if (description != null) {errorMap.put("description", description);}

            errorContents = JSONObject.toJSONString(errorMap);
        }
        return errorContents;
    }

    private String getErrorText(ErrorCodeStructure accessNotAllowedError) {
        if (accessNotAllowedError != null) {
            return accessNotAllowedError.getErrorText();
        }
        return null;
    }
    private String getDescriptionText(ErrorDescriptionStructure description) {
        if (description != null) {
            return description.getValue();
        }
        return null;
    }

}
