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

package no.rutebanken.anshar.routes.siri.handlers;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.json.simple.JSONObject;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.ErrorCodeStructure;
import uk.org.siri.siri20.ErrorDescriptionStructure;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.RequestorRef;
import uk.org.siri.siri20.ServiceDeliveryErrorConditionElement;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;
import uk.org.siri.siri20.SubscriptionResponseStructure;
import uk.org.siri.siri20.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri20.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri20.VehicleMonitoringRequestStructure;
import uk.org.siri.siri20.VehicleRef;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.datatype.Duration;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Service
public class SiriHandler {

    private final Logger logger = LoggerFactory.getLogger(SiriHandler.class);

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
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private HealthManager healthManager;

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    private PrometheusMetricsService metrics;

    public Siri handleIncomingSiri(String subscriptionId, InputStream xml) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, null, -1);
    }

    private Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, int maxSize) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, datasetId, null, maxSize, null);
    }

    /**
     *
     * @param subscriptionId SubscriptionId
     * @param xml SIRI-request as XML
     * @param datasetId Optional datasetId
     * @param outboundIdMappingPolicy Defines outbound idmapping-policy
     * @return
     */
    public Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, datasetId, null, outboundIdMappingPolicy, maxSize, clientTrackingName);
    }

    public Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, List<String> excludedDatasetIdList, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) throws UnmarshalException {
        try {
            if (subscriptionId != null) {
                processSiriClientRequest(subscriptionId, xml);
            } else {
                Siri incoming = SiriValueTransformer.parseXml(xml);

                return processSiriServerRequest(incoming, datasetId, excludedDatasetIdList, outboundIdMappingPolicy, maxSize, clientTrackingName);
            }
        } catch (UnmarshalException e) {
            throw e;
        } catch (JAXBException | XMLStreamException e) {
            logger.warn("Caught exception when parsing incoming XML", e);
        }
        return null;
    }

    public Siri handleSiriCacheRequest(
        InputStream body, String datasetId, String clientTrackingName
    ) throws XMLStreamException, JAXBException {

        Siri incoming = SiriValueTransformer.parseXml(body);

        if (incoming.getServiceRequest() != null) {
            ServiceRequest serviceRequest = incoming.getServiceRequest();
            String requestorRef = null;

            Siri serviceResponse = null;

            if (serviceRequest.getRequestorRef() != null) {
                requestorRef = serviceRequest.getRequestorRef().getValue();
            }

            SiriDataType dataType = null;
            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                dataType = SiriDataType.SITUATION_EXCHANGE;

                final Collection<PtSituationElement> elements = situations.getAllCachedUpdates(requestorRef,
                    datasetId,
                    clientTrackingName
                );
                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse =  siriObjectFactory.createSXServiceDelivery(elements);

            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                dataType = SiriDataType.VEHICLE_MONITORING;

                final Collection<VehicleActivityStructure> elements = vehicleActivities.getAllCachedUpdates(
                    requestorRef,
                    datasetId,
                    clientTrackingName
                );
                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createVMServiceDelivery(elements);

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                dataType = SiriDataType.ESTIMATED_TIMETABLE;

                final Collection<EstimatedVehicleJourney> elements = estimatedTimetables.getAllCachedUpdates(requestorRef, datasetId, clientTrackingName);

                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createETServiceDelivery(elements);

            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
                return SiriValueTransformer.transform(
                    serviceResponse,
                    MappingAdapterPresets.getOutboundAdapters(dataType, OutboundIdMappingPolicy.DEFAULT),
                    false,
                    false
                );
            }
        }
        return null;
    }


    /**
     * Handling incoming requests from external clients
     *
     * @param incoming
     * @param excludedDatasetIdList
     * @throws JAXBException
     */
    private Siri processSiriServerRequest(Siri incoming, String datasetId, List<String> excludedDatasetIdList, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) {

        if (maxSize < 0) {
            maxSize = configuration.getDefaultMaxSize();

            if (datasetId != null) {
                maxSize = Integer.MAX_VALUE;
            }
        }

        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest with ID-policy {}.", outboundIdMappingPolicy);
            return serverSubscriptionManager.handleSubscriptionRequest(incoming.getSubscriptionRequest(), datasetId, outboundIdMappingPolicy, clientTrackingName);

        } else if (incoming.getTerminateSubscriptionRequest() != null) {
            logger.info("Handling terminateSubscriptionrequest...");
            TerminateSubscriptionRequestStructure terminateSubscriptionRequest = incoming.getTerminateSubscriptionRequest();
            if (terminateSubscriptionRequest.getSubscriptionReves() != null && !terminateSubscriptionRequest.getSubscriptionReves().isEmpty()) {
                String subscriptionRef = terminateSubscriptionRequest.getSubscriptionReves().get(0).getValue();

                serverSubscriptionManager.terminateSubscription(subscriptionRef, configuration.processAdmin());
                if (configuration.processAdmin()) {
                    return siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
                }
            }
        } else if (incoming.getCheckStatusRequest() != null) {
            logger.info("Handling checkStatusRequest...");
            return serverSubscriptionManager.handleCheckStatusRequest(incoming.getCheckStatusRequest());
        } else if (incoming.getServiceRequest() != null) {
            logger.debug("Handling serviceRequest with ID-policy {}.", outboundIdMappingPolicy);
            ServiceRequest serviceRequest = incoming.getServiceRequest();
            String requestorRef = null;

            Siri serviceResponse = null;

            if (serviceRequest.getRequestorRef() != null) {
                requestorRef = serviceRequest.getRequestorRef().getValue();
            }
            SiriDataType dataType = null;
            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                dataType = SiriDataType.SITUATION_EXCHANGE;
                serviceResponse = situations.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize);
            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                dataType = SiriDataType.VEHICLE_MONITORING;
                Map<Class, Set<String>> filterMap = new HashMap<>();
                for (VehicleMonitoringRequestStructure req : serviceRequest.getVehicleMonitoringRequests()) {
                    LineRef lineRef = req.getLineRef();
                    if (lineRef != null) {
                        Set<String> linerefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class): new HashSet<>();
                        linerefList.add(lineRef.getValue());
                        filterMap.put(LineRef.class, linerefList);
                    }
                    VehicleRef vehicleRef = req.getVehicleRef();
                    if (vehicleRef != null) {
                        Set<String> vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class): new HashSet<>();
                        vehicleRefList.add(vehicleRef.getValue());
                        filterMap.put(VehicleRef.class, vehicleRefList);
                    }
                }
                if (!filterMap.isEmpty()) {
                    //Filter is specified - return data even if they have not changed
                    requestorRef = null;
                }

                Siri siri = vehicleActivities.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize);

                serviceResponse = SiriHelper.filterSiriPayload(siri, filterMap);
            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                dataType = SiriDataType.ESTIMATED_TIMETABLE;
                Duration previewInterval = serviceRequest.getEstimatedTimetableRequests().get(0).getPreviewInterval();
                long previewIntervalInMillis = -1;

                if (previewInterval != null) {
                    previewIntervalInMillis = previewInterval.getTimeInMillis(new Date());
                }

                serviceResponse = estimatedTimetables.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, previewIntervalInMillis);
            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
                return SiriValueTransformer.transform(
                    serviceResponse,
                    MappingAdapterPresets.getOutboundAdapters(dataType, outboundIdMappingPolicy),
                    false,
                    false
                );
            }
        }

        return null;
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
    private void processSiriClientRequest(String subscriptionId, InputStream xml)
            throws XMLStreamException, JAXBException {
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup != null) {

            int receivedBytes;
            try {
                receivedBytes = xml.available();
            } catch (IOException e) {
                receivedBytes = 0;
            }
long t1 = System.currentTimeMillis();
            Siri incoming = SiriXml.parseXml(xml);
long t2 = System.currentTimeMillis();
            logger.info("Parsing XML took {} ms, {} bytes", (t2-t1), receivedBytes);
            if (incoming == null) {
                return;
            }

            if (incoming.getHeartbeatNotification() != null) {
                subscriptionManager.touchSubscription(subscriptionId);
                logger.info("Heartbeat - {}", subscriptionSetup);
            } else if (incoming.getCheckStatusResponse() != null) {
                logger.info("Incoming CheckStatusResponse [{}], reporting ServiceStartedTime: {}", subscriptionSetup, incoming.getCheckStatusResponse().getServiceStartedTime());
                subscriptionManager.touchSubscription(subscriptionId, incoming.getCheckStatusResponse().getServiceStartedTime());
            } else if (incoming.getSubscriptionResponse() != null) {
                SubscriptionResponseStructure subscriptionResponse = incoming.getSubscriptionResponse();
                subscriptionResponse.getResponseStatuses().forEach(responseStatus -> {
                    if (responseStatus.isStatus() == null ||
                        (responseStatus.isStatus() != null && responseStatus.isStatus())) {

                        // If no status is provided it is handled as "true"

                        subscriptionManager.activatePendingSubscription(subscriptionId);
                    }
                });

            } else if (incoming.getTerminateSubscriptionResponse() != null) {
                TerminateSubscriptionResponseStructure terminateSubscriptionResponse = incoming.getTerminateSubscriptionResponse();

                logger.info("Subscription terminated {}", subscriptionSetup);

            } else if (incoming.getDataReadyNotification() != null) {
                //Handled using camel routing
            } else if (incoming.getServiceDelivery() != null) {
                boolean deliveryContainsData = false;
                healthManager.dataReceived();

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
                    List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
                    logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetup);

                    List<PtSituationElement> addedOrUpdated = new ArrayList<>();
                    if (situationExchangeDeliveries != null) {
                        situationExchangeDeliveries.forEach(sx -> {
                                    if (sx != null) {
                                        if (sx.isStatus() != null && !sx.isStatus()) {
                                            logger.info(getErrorContents(sx.getErrorCondition()));
                                        } else {
                                            if (sx.getSituations() != null && sx.getSituations().getPtSituationElements() != null) {
                                                if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                    Map<String, List<PtSituationElement>> situationsByCodespace = splitSituationsByCodespace(sx.getSituations().getPtSituationElements());
                                                    for (String codespace : situationsByCodespace.keySet()) {

                                                        // List containing added situations for current codespace
                                                        List<PtSituationElement> addedSituations = new ArrayList();

                                                        addedSituations.addAll(situations.addAll(
                                                            codespace,
                                                            situationsByCodespace.get(codespace)
                                                        ));

                                                        // Push updates to subscribers on this codespace
                                                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedSituations, codespace);

                                                        // Add to complete list of added situations
                                                        addedOrUpdated.addAll(addedSituations);

                                                    }

                                                } else {

                                                    addedOrUpdated.addAll(situations.addAll(
                                                        subscriptionSetup.getDatasetId(),
                                                        sx.getSituations().getPtSituationElements()
                                                    ));
                                                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());
                                                }
                                            }
                                        }
                                    }
                                }
                        );
                    }
                    deliveryContainsData = addedOrUpdated.size() > 0;

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active SX-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
                    List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
                    logger.info("Got VM-delivery: Subscription [{}] {}", subscriptionSetup, subscriptionSetup.forwardPositionData() ? "- Position only":"");

                    List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
                    if (vehicleMonitoringDeliveries != null) {
                        vehicleMonitoringDeliveries.forEach(vm -> {
                                    if (vm != null) {
                                        if (vm.isStatus() != null && !vm.isStatus()) {
                                            logger.info(getErrorContents(vm.getErrorCondition()));
                                        } else {
                                            if (vm.getVehicleActivities() != null) {
                                                if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                    Map<String, List<VehicleActivityStructure>> vehiclesByCodespace = splitVehicleMonitoringByCodespace(vm.getVehicleActivities());
                                                    for (String codespace : vehiclesByCodespace.keySet()) {

                                                        // List containing added situations for current codespace
                                                        List<VehicleActivityStructure> addedVehicles = new ArrayList();

                                                        addedVehicles.addAll(vehicleActivities.addAll(
                                                                codespace,
                                                                vehiclesByCodespace.get(codespace)
                                                        ));

                                                        // Push updates to subscribers on this codespace
                                                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedVehicles, codespace);

                                                        // Add to complete list of added situations
                                                        addedOrUpdated.addAll(addedVehicles);

                                                    }

                                                } else {
                                                    addedOrUpdated.addAll(
                                                            vehicleActivities.addAll(subscriptionSetup.getDatasetId(), vm.getVehicleActivities())
                                                    );
                                                }
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active VM-elements: {}, current delivery: {}, {}", vehicleActivities.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                    logger.info("Got ET-delivery: Subscription {}", subscriptionSetup);

                    List<EstimatedVehicleJourney> addedOrUpdated = new ArrayList<>();
                    if (estimatedTimetableDeliveries != null) {
                        estimatedTimetableDeliveries.forEach(et -> {
                                    if (et != null) {
                                        if (et.isStatus() != null && !et.isStatus()) {
                                            logger.info(getErrorContents(et.getErrorCondition()));
                                        } else {
                                            if (et.getEstimatedJourneyVersionFrames() != null) {
                                                et.getEstimatedJourneyVersionFrames().forEach(versionFrame -> {
                                                    if (versionFrame != null && versionFrame.getEstimatedVehicleJourneies() != null) {
                                                        if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                            Map<String, List<EstimatedVehicleJourney>> journeysByCodespace = splitEstimatedTimetablesByCodespace(versionFrame.getEstimatedVehicleJourneies());
                                                            for (String codespace : journeysByCodespace.keySet()) {

                                                                // List containing added situations for current codespace
                                                                List<EstimatedVehicleJourney> addedJourneys = new ArrayList();

                                                                addedJourneys.addAll(estimatedTimetables.addAll(
                                                                        codespace,
                                                                        journeysByCodespace.get(codespace)
                                                                ));

                                                                // Push updates to subscribers on this codespace
                                                                serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedJourneys, codespace);

                                                                // Add to complete list of added situations
                                                                addedOrUpdated.addAll(addedJourneys);

                                                            }

                                                        } else {
                                                            addedOrUpdated.addAll(
                                                                    estimatedTimetables.addAll(subscriptionSetup.getDatasetId(), versionFrame.getEstimatedVehicleJourneies())
                                                            );
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active ET-elements: {}, current delivery: {}, {}", estimatedTimetables.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }

                if (deliveryContainsData) {
                    subscriptionManager.dataReceived(subscriptionId, receivedBytes);
                } else {
                    subscriptionManager.touchSubscription(subscriptionId);
                }
            } else {
                try {
                    logger.info("Unsupported SIRI-request:" + SiriXml.toXml(incoming));
                } catch (JAXBException e) {
                    //Ignore
                }
            }
        } else {
            logger.debug("ServiceDelivery for invalid subscriptionId [{}] ignored.", subscriptionId);
        }
    }

    private Map<String, List<PtSituationElement>> splitSituationsByCodespace(
        List<PtSituationElement> ptSituationElements
    ) {
        Map<String, List<PtSituationElement>> result = new HashMap<>();
        for (PtSituationElement ptSituationElement : ptSituationElements) {
            final RequestorRef participantRef = ptSituationElement.getParticipantRef();
            if (participantRef != null) {
                final String codespace = getOriginalId(participantRef.getValue());

                //Override mapped value if present
                participantRef.setValue(codespace);

                final List<PtSituationElement> situations = result.getOrDefault(
                    codespace,
                    new ArrayList<>()
                );

                situations.add(ptSituationElement);
                result.put(codespace, situations);
            }
        }
        return result;
    }

    private Map<String, List<VehicleActivityStructure>> splitVehicleMonitoringByCodespace(
            List<VehicleActivityStructure> activityStructures
    ) {
        Map<String, List<VehicleActivityStructure>> result = new HashMap<>();
        for (VehicleActivityStructure vmElement : activityStructures) {
            if (vmElement.getMonitoredVehicleJourney() != null) {

                final String dataSource = vmElement.getMonitoredVehicleJourney().getDataSource();
                if (dataSource != null) {

                    final String codespace = getOriginalId(dataSource);
                    //Override mapped value if present
                    vmElement.getMonitoredVehicleJourney().setDataSource(codespace);

                    final List<VehicleActivityStructure> vehicles = result.getOrDefault(
                            codespace,
                            new ArrayList<>()
                    );

                    vehicles.add(vmElement);
                    result.put(codespace, vehicles);
                }
            }
        }
        return result;
    }

    private Map<String, List<EstimatedVehicleJourney>> splitEstimatedTimetablesByCodespace(
            List<EstimatedVehicleJourney> estimatedVehicleJourneys
    ) {
        Map<String, List<EstimatedVehicleJourney>> result = new HashMap<>();
        for (EstimatedVehicleJourney etElement : estimatedVehicleJourneys) {
            if (etElement.getDataSource() != null) {

                final String dataSource = etElement.getDataSource();
                if (dataSource != null) {

                    final String codespace = getOriginalId(dataSource);
                    //Override mapped value if present
                    etElement.setDataSource(codespace);

                    final List<EstimatedVehicleJourney> etJourneys = result.getOrDefault(
                            codespace,
                            new ArrayList<>()
                    );

                    etJourneys.add(etElement);
                    result.put(codespace, etJourneys);
                }
            }
        }
        return result;
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

    public static OutboundIdMappingPolicy getIdMappingPolicy(String useOriginalId) {
        OutboundIdMappingPolicy outboundIdMappingPolicy = OutboundIdMappingPolicy.DEFAULT;
        if (useOriginalId != null) {
            if (Boolean.valueOf(useOriginalId)) {
                outboundIdMappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
            }
        }
        return outboundIdMappingPolicy;
    }
}
