package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SiriObjectFactory {

    private static final String SIRI_VERSION = "2.0";
    private static Logger logger = LoggerFactory.getLogger(SiriObjectFactory.class);


    public Instant serverStartTime;

    public SiriObjectFactory(@Autowired Instant serverStartTime) {
        this.serverStartTime = serverStartTime;
    }
    
    public static Siri createSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        SubscriptionRequest request = null;

        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)) {
            request = createSituationExchangeSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
            request = createVehicleMonitoringSubscriptionRequest(subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
            request = createEstimatedTimetableSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
            request = createProductionTimetableSubscriptionRequest(subscriptionSetup.getRequestorRef(), subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap());
        }
        siri.setSubscriptionRequest(request);

        return siri;
    }


    public static Siri createServiceRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        ServiceRequest request = new ServiceRequest();
        request.setRequestTimestamp(ZonedDateTime.now());
        request.setRequestorRef(createRequestorRef());

        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)) {
            request.getSituationExchangeRequests().add(createSituationExchangeRequestStructure(subscriptionSetup.getPreviewInterval()));

        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
            request.getVehicleMonitoringRequests().add(createVehicleMonitoringRequestStructure());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
            request.getEstimatedTimetableRequests().add(createEstimatedTimetableRequestStructure(subscriptionSetup.getPreviewInterval()));
        }

        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
            request.getProductionTimetableRequests().add(createProductionTimetableRequestStructure());
        }

        siri.setServiceRequest(request);

        return siri;
    }


    public static Siri createDataSupplyRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        DataSupplyRequestStructure request = new DataSupplyRequestStructure();
        request.setRequestTimestamp(ZonedDateTime.now());
        request.setConsumerRef(createRequestorRef(subscriptionSetup.getRequestorRef()));

        siri.setDataSupplyRequest(request);

        return siri;
    }

    public static Siri createCheckStatusRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        CheckStatusRequestStructure statusRequest = new CheckStatusRequestStructure();
        statusRequest.setRequestTimestamp(ZonedDateTime.now());
        statusRequest.setMessageIdentifier(createMessageIdentifier());
        statusRequest.setRequestorRef(createRequestorRef(subscriptionSetup.getRequestorRef()));
        siri.setCheckStatusRequest(statusRequest);

        return siri;
    }

    private static SituationExchangeRequestStructure createSituationExchangeRequestStructure(Duration previewInterval) {
        SituationExchangeRequestStructure sxRequest = new SituationExchangeRequestStructure();
        sxRequest.setRequestTimestamp(ZonedDateTime.now());
        sxRequest.setVersion(SIRI_VERSION);
        sxRequest.setMessageIdentifier(createMessageIdentifier());
        if (previewInterval != null) {
            sxRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }
        return sxRequest;
    }

    private static VehicleMonitoringRequestStructure createVehicleMonitoringRequestStructure() {
        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion(SIRI_VERSION);
        vmRequest.setMessageIdentifier(createMessageIdentifier());
        return vmRequest;
    }

    private static EstimatedTimetableRequestStructure createEstimatedTimetableRequestStructure(Duration previewInterval) {
        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion(SIRI_VERSION);
        etRequest.setMessageIdentifier(createMessageIdentifier());
        if (previewInterval != null) {
            etRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }
        return etRequest;
    }

    private static ProductionTimetableRequestStructure createProductionTimetableRequestStructure() {
        ProductionTimetableRequestStructure ptRequest = new ProductionTimetableRequestStructure();
        ptRequest.setRequestTimestamp(ZonedDateTime.now());
        ptRequest.setVersion(SIRI_VERSION);
        ptRequest.setMessageIdentifier(createMessageIdentifier());
        return ptRequest;
    }

    private static SubscriptionRequest createSituationExchangeSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        SituationExchangeRequestStructure sxRequest = createSituationExchangeRequestStructure(null);
        sxRequest.setPreviewInterval(createDataTypeFactory().newDuration("P1Y"));

        if (filterMap != null) {
            Set<Object> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && vehicleRefs.size() > 0) {
                Object next = vehicleRefs.iterator().next();
                if (next instanceof VehicleRef)  {
                    sxRequest.setVehicleRef((VehicleRef) next);
                }
            }
        }

        SituationExchangeSubscriptionStructure sxSubscriptionReq = new SituationExchangeSubscriptionStructure();
        sxSubscriptionReq.setSituationExchangeRequest(sxRequest);
        sxSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        sxSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        sxSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        request.getSituationExchangeSubscriptionRequests().add(sxSubscriptionReq);

        return request;
    }

    private static SubscriptionRequest createVehicleMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef,heartbeatInterval, address);

        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion(SIRI_VERSION);

        if (filterMap != null) {
            Set lineRefs = filterMap.get(LineRef.class);
            if (lineRefs != null && lineRefs.size() > 0) {
                Object next = lineRefs.iterator().next();
                if (next instanceof LineRef) {
                    vmRequest.setLineRef((LineRef) next);
                }
            }
            Set<Object> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && vehicleRefs.size() > 0) {
                Object next = vehicleRefs.iterator().next();
                if (next instanceof VehicleRef)  {
                    vmRequest.setVehicleRef((VehicleRef) next);
                }
            }
        }

        VehicleMonitoringSubscriptionStructure vmSubscriptionReq = new VehicleMonitoringSubscriptionStructure();
        vmSubscriptionReq.setVehicleMonitoringRequest(vmRequest);
        vmSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        vmSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        vmSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        request.getVehicleMonitoringSubscriptionRequests().add(vmSubscriptionReq);

        return request;
    }


    private static SubscriptionRequest createEstimatedTimetableSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion(SIRI_VERSION);
        etRequest.setPreviewInterval(createDataTypeFactory().newDuration("PT24H"));

        if (filterMap != null) {
            if (filterMap.size() > 0) {
                EstimatedTimetableRequestStructure.Lines lines = new EstimatedTimetableRequestStructure.Lines();

                Set lineRefs = filterMap.get(LineDirectionStructure.class);
                for (Object lineref : lineRefs) {
                    if (lineref != null &&
                            lineref instanceof LineDirectionStructure) {
                        lines.getLineDirections().add((LineDirectionStructure) lineref);
                    }
                }

                etRequest.setLines(lines);
            }
        }

        EstimatedTimetableSubscriptionStructure etSubscriptionReq = new EstimatedTimetableSubscriptionStructure();
        etSubscriptionReq.setEstimatedTimetableRequest(etRequest);
        etSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        etSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        etSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        etSubscriptionReq.setChangeBeforeUpdates(createDataTypeFactory().newDuration(heartbeatInterval));

        request.getEstimatedTimetableSubscriptionRequests().add(etSubscriptionReq);

        return request;
    }


    private static SubscriptionRequest createProductionTimetableSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        ProductionTimetableRequestStructure ptRequest = new ProductionTimetableRequestStructure();
        ptRequest.setRequestTimestamp(ZonedDateTime.now());
        ptRequest.setVersion(SIRI_VERSION);

        if (filterMap != null) {
            if (filterMap.size() > 0) {
                logger.info("TODO: Implement filtering");
            }
        }

        ProductionTimetableSubscriptionRequest ptSubscriptionReq = new ProductionTimetableSubscriptionRequest();
        ptSubscriptionReq.setProductionTimetableRequest(ptRequest);
        ptSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        ptSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        ptSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        request.getProductionTimetableSubscriptionRequests().add(ptSubscriptionReq);

        return request;
    }

    private static SubscriptionRequest createSubscriptionRequest(String requestorRef, String heartbeatInterval, String address) {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setRequestorRef(createRequestorRef(requestorRef));
        request.setMessageIdentifier(createMessageIdentifier(UUID.randomUUID().toString()));
        request.setConsumerAddress(address);
        request.setRequestTimestamp(ZonedDateTime.now());

        SubscriptionContextStructure ctx = new SubscriptionContextStructure();
        ctx.setHeartbeatInterval(createDataTypeFactory().newDuration(heartbeatInterval));

        request.setSubscriptionContext(ctx);
        return request;
    }

    public static Siri createTerminateSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        if (subscriptionSetup == null) {
            return null;
        }
        return createTerminateSubscriptionRequest(subscriptionSetup.getSubscriptionId(), createRequestorRef(subscriptionSetup.getRequestorRef()));
    }

    private static Siri createTerminateSubscriptionRequest(String subscriptionId, RequestorRef requestorRef) {
        if (requestorRef == null || requestorRef.getValue() == null) {
            logger.warn("RequestorRef cannot be null");
            return null;
        }
        TerminateSubscriptionRequestStructure terminationReq = new TerminateSubscriptionRequestStructure();

        terminationReq.setRequestTimestamp(ZonedDateTime.now());
        terminationReq.getSubscriptionReves().add(createSubscriptionIdentifier(subscriptionId));
        terminationReq.setRequestorRef(requestorRef);
        terminationReq.setMessageIdentifier(createMessageIdentifier(UUID.randomUUID().toString()));

        Siri siri = createSiriObject();
        siri.setTerminateSubscriptionRequest(terminationReq);
        return siri;
    }

    public static RequestorRef createRequestorRef(String value) {
        if(value == null) {
        	value = UUID.randomUUID().toString();
        }
    	RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue(value);
        return requestorRef;
    }

    private static RequestorRef createRequestorRef() {
        return createRequestorRef(UUID.randomUUID().toString());
    }

    private static SubscriptionQualifierStructure createSubscriptionIdentifier(String subscriptionId) {
        SubscriptionQualifierStructure subscriptionRef = new SubscriptionQualifierStructure();
        subscriptionRef.setValue(subscriptionId);
        return subscriptionRef;
    }

    private static MessageQualifierStructure createMessageIdentifier(String value) {
        MessageQualifierStructure msgId = new MessageQualifierStructure();
        msgId.setValue(value);
        return msgId;
    }

    private static MessageQualifierStructure createMessageIdentifier() {
        return createMessageIdentifier(UUID.randomUUID().toString());
    }

    public Siri createSXServiceDelivery(Collection<PtSituationElement> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = new ServiceDelivery();
        SituationExchangeDeliveryStructure deliveryStructure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().addAll(elements);
        deliveryStructure.setSituations(situations);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getSituationExchangeDeliveries().add(deliveryStructure);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createVMServiceDelivery(Collection<VehicleActivityStructure> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = new ServiceDelivery();
        VehicleMonitoringDeliveryStructure deliveryStructure = new VehicleMonitoringDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        deliveryStructure.getVehicleActivities().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getVehicleMonitoringDeliveries().add(deliveryStructure);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createETServiceDelivery(Collection<EstimatedVehicleJourney> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure deliveryStructure = new EstimatedTimetableDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        EstimatedVersionFrameStructure estimatedVersionFrameStructure = new EstimatedVersionFrameStructure();
        estimatedVersionFrameStructure.setRecordedAtTime(ZonedDateTime.now());
        estimatedVersionFrameStructure.getEstimatedVehicleJourneies().addAll(elements);
        deliveryStructure.getEstimatedJourneyVersionFrames().add(estimatedVersionFrameStructure);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        delivery.getEstimatedTimetableDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createPTServiceDelivery(Collection<ProductionTimetableDeliveryStructure> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getProductionTimetableDeliveries().addAll(elements);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public static DatatypeFactory createDataTypeFactory() {
        try {
            return DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public Siri createHeartbeatNotification(String requestorRef) {
        Siri siri = createSiriObject();
        HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
        heartbeat.setStatus(true);
        heartbeat.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        heartbeat.setRequestTimestamp(ZonedDateTime.now());
        heartbeat.setProducerRef(createRequestorRef(requestorRef));
        siri.setHeartbeatNotification(heartbeat);
        return siri;
    }

    public Siri createCheckStatusResponse() {
        Siri siri = createSiriObject();
        CheckStatusResponseStructure response = new CheckStatusResponseStructure();
        response.setStatus(true);
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        response.setShortestPossibleCycle(createDataTypeFactory().newDuration(60000));
        siri.setCheckStatusResponse(response);
        return siri;
    }

    private static Siri createSiriObject() {
        Siri siri = new Siri();
        siri.setVersion(SIRI_VERSION);
        return siri;
    }

    public Siri createSubscriptionResponse(String subscriptionRef, boolean status, String errorText) {
        Siri siri = createSiriObject();
        SubscriptionResponseStructure response = new SubscriptionResponseStructure();
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        response.setRequestMessageRef(createMessageIdentifier());
        response.setResponderRef(createRequestorRef(subscriptionRef));
        response.setResponseTimestamp(ZonedDateTime.now());


        ResponseStatus responseStatus = new ResponseStatus();
        responseStatus.setResponseTimestamp(ZonedDateTime.now());
        responseStatus.setRequestMessageRef(createMessageIdentifier());
        responseStatus.setSubscriptionRef(createSubscriptionIdentifier(subscriptionRef));
        responseStatus.setStatus(status);

        if (errorText != null) {
            ServiceDeliveryErrorConditionElement error = new ServiceDeliveryErrorConditionElement();
            OtherErrorStructure otherError = new OtherErrorStructure();
            otherError.setErrorText(errorText);
            error.setOtherError(otherError);
            responseStatus.setErrorCondition(error);
        }

        response.getResponseStatuses().add(responseStatus);

        siri.setSubscriptionResponse(response);
        return siri;
    }

    public Siri createTerminateSubscriptionResponse(String subscriptionRef) {
        Siri siri = createSiriObject();
        TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
        TerminationResponseStatusStructure status = new TerminationResponseStatusStructure();
        status.setSubscriptionRef(createSubscriptionIdentifier(subscriptionRef));
        status.setResponseTimestamp(ZonedDateTime.now());

        response.getTerminationResponseStatuses().add(status);
        siri.setTerminateSubscriptionResponse(response);
        return siri;
    }

    /**
     * Creates a deep copy of provided object
     * @param siri
     * @return
     * @throws JAXBException
     */
    public static Siri deepCopy(Siri siri) throws JAXBException {
        return SiriXml.parseXml(SiriXml.toXml(siri));
    }
}
