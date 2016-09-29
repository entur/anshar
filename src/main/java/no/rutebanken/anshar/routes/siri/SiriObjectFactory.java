package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SiriObjectFactory {

    private static Logger logger = LoggerFactory.getLogger(SiriObjectFactory.class);

    private static final ZonedDateTime serverStartTime = ZonedDateTime.now();

    public  SiriObjectFactory() {
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
            request.getSituationExchangeRequests().add(createSituationExchangeRequestStructure());

        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
            request.getVehicleMonitoringRequests().add(createVehicleMonitoringRequestStructure());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
            request.getEstimatedTimetableRequests().add(createEstimatedTimetableRequestStructure());
        }

        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
            request.getProductionTimetableRequests().add(createProductionTimetableRequestStructure());
        }

        siri.setServiceRequest(request);

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

    private static SituationExchangeRequestStructure createSituationExchangeRequestStructure() {
        SituationExchangeRequestStructure sxRequest = new SituationExchangeRequestStructure();
        sxRequest.setRequestTimestamp(ZonedDateTime.now());
        sxRequest.setVersion("2.0");
        sxRequest.setMessageIdentifier(createMessageIdentifier());
        return sxRequest;
    }

    private static VehicleMonitoringRequestStructure createVehicleMonitoringRequestStructure() {
        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion("2.0");
        vmRequest.setMessageIdentifier(createMessageIdentifier());
        return vmRequest;
    }

    private static EstimatedTimetableRequestStructure createEstimatedTimetableRequestStructure() {
        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion("2.0");
        etRequest.setMessageIdentifier(createMessageIdentifier());
        return etRequest;
    }

    private static ProductionTimetableRequestStructure createProductionTimetableRequestStructure() {
        ProductionTimetableRequestStructure ptRequest = new ProductionTimetableRequestStructure();
        ptRequest.setRequestTimestamp(ZonedDateTime.now());
        ptRequest.setVersion("2.0");
        ptRequest.setMessageIdentifier(createMessageIdentifier());
        return ptRequest;
    }

    private static SubscriptionRequest createSituationExchangeSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<String>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        SituationExchangeRequestStructure sxRequest = createSituationExchangeRequestStructure();
        sxRequest.setPreviewInterval(createDataTypeFactory().newDuration("P1Y"));

        if (filterMap != null) {
            Set<String> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && vehicleRefs.size() > 0) {
                VehicleRef vehicleRef = new VehicleRef();
                vehicleRef.setValue(vehicleRefs.iterator().next());
                sxRequest.setVehicleRef(vehicleRef);
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

    private static SubscriptionRequest createVehicleMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<String>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef,heartbeatInterval, address);

        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion("2.0");

        if (filterMap != null) {
            Set<String> lineRefs = filterMap.get(LineRef.class);
            if (lineRefs != null && lineRefs.size() > 0) {
                LineRef lineRef = new LineRef();
                lineRef.setValue(lineRefs.iterator().next());
                vmRequest.setLineRef(lineRef);
            }
            Set<String> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && vehicleRefs.size() > 0) {
                VehicleRef vehicleRef = new VehicleRef();
                vehicleRef.setValue(vehicleRefs.iterator().next());
                vmRequest.setVehicleRef(vehicleRef);
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


    private static SubscriptionRequest createEstimatedTimetableSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<String>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion("2.0");
        etRequest.setPreviewInterval(createDataTypeFactory().newDuration("P1D"));

        if (filterMap != null) {
            if (filterMap.size() > 0) {
                logger.info("TODO: Implement filtering");
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


    private static SubscriptionRequest createProductionTimetableSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<String>> filterMap) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        ProductionTimetableRequestStructure ptRequest = new ProductionTimetableRequestStructure();
        ptRequest.setRequestTimestamp(ZonedDateTime.now());
        ptRequest.setVersion("2.0");

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

    public static Siri createSXServiceDelivery(List<PtSituationElement> elements) {
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

    public static Siri createVMServiceDelivery(List<VehicleActivityStructure> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = new ServiceDelivery();
        VehicleMonitoringDeliveryStructure deliveryStructure = new VehicleMonitoringDeliveryStructure();
        deliveryStructure.setVersion("2.0");
        deliveryStructure.getVehicleActivities().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getVehicleMonitoringDeliveries().add(deliveryStructure);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public static Siri createETServiceDelivery(List<EstimatedVehicleJourney> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure deliveryStructure = new EstimatedTimetableDeliveryStructure();
        deliveryStructure.setVersion("2.0");
        EstimatedVersionFrameStructure estimatedVersionFrameStructure = new EstimatedVersionFrameStructure();
        estimatedVersionFrameStructure.setRecordedAtTime(ZonedDateTime.now());
        estimatedVersionFrameStructure.getEstimatedVehicleJourneies().addAll(elements);
        deliveryStructure.getEstimatedJourneyVersionFrames().add(estimatedVersionFrameStructure);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        delivery.getEstimatedTimetableDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public static Siri createPTServiceDelivery(List<ProductionTimetableDeliveryStructure> elements) {
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

    public static Siri createHeartbeatNotification(String requestorRef) {
        Siri siri = createSiriObject();
        HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
        heartbeat.setStatus(true);
        heartbeat.setServiceStartedTime(serverStartTime);
        heartbeat.setRequestTimestamp(ZonedDateTime.now());
        heartbeat.setProducerRef(createRequestorRef(requestorRef));
        siri.setHeartbeatNotification(heartbeat);
        return siri;
    }

    public static Siri createCheckStatusResponse() {
        Siri siri = createSiriObject();
        CheckStatusResponseStructure response = new CheckStatusResponseStructure();
        response.setStatus(true);
        response.setServiceStartedTime(serverStartTime);
        response.setShortestPossibleCycle(createDataTypeFactory().newDuration(60000));
        siri.setCheckStatusResponse(response);
        return siri;
    }

    private static Siri createSiriObject() {
        Siri siri = new Siri();
        siri.setVersion("2.0");
        return siri;
    }

    public static Siri createSubscriptionResponse(String subscriptionRef, boolean status, String errorText) {
        Siri siri = createSiriObject();
        SubscriptionResponseStructure response = new SubscriptionResponseStructure();
        response.setServiceStartedTime(serverStartTime);
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

    public static Siri createTerminateSubscriptionResponse(String subscriptionRef) {
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
