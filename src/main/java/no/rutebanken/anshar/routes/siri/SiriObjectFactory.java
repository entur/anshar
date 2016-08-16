package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class SiriObjectFactory {

    private static Logger logger = LoggerFactory.getLogger(SiriObjectFactory.class);

    public  SiriObjectFactory() {
    }

    public static Siri createSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        logger.trace("Creating subscription-request ");
        Siri siri = new Siri();
        siri.setVersion("2.0");

        SubscriptionRequest request = null;

        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)) {
            request = createSituationExchangeSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING)) {
            request = createVehicleMonitoringSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)) {
            request = createEstimatedTimetableSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE)) {
            request = createProductionTimetableSubscriptionRequest(subscriptionSetup.getRequestorRef(), subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval().toString(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription());
        }
        siri.setSubscriptionRequest(request);

        return siri;
    }


    public static Siri createServiceRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = new Siri();
        siri.setVersion("2.0");

        ServiceRequest request = new ServiceRequest();
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
        Siri siri = new Siri();
        siri.setVersion("2.0");

        CheckStatusRequestStructure statusRequest = new CheckStatusRequestStructure();
        statusRequest.setRequestTimestamp(ZonedDateTime.now());
        statusRequest.setMessageIdentifier(createMessageIdentifier());
        statusRequest.setRequestorRef(createRequestorRef(subscriptionSetup.getRequestorRef()));
        siri.setCheckStatusRequest(statusRequest);

        return siri;
    }

    private static SubscriptionRequest createSituationExchangeSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        SituationExchangeRequestStructure sxRequest = createSituationExchangeRequestStructure();

        SituationExchangeSubscriptionStructure sxSubscriptionReq = new SituationExchangeSubscriptionStructure();
        sxSubscriptionReq.setSituationExchangeRequest(sxRequest);
        sxSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        sxSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        sxSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        request.getSituationExchangeSubscriptionRequests().add(sxSubscriptionReq);

        return request;
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

    private static SubscriptionRequest createVehicleMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef,heartbeatInterval, address);

        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion("2.0");
        

        VehicleMonitoringSubscriptionStructure vmSubscriptionReq = new VehicleMonitoringSubscriptionStructure();
        vmSubscriptionReq.setVehicleMonitoringRequest(vmRequest);
        vmSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        vmSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        vmSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        


        request.getVehicleMonitoringSubscriptionRequests().add(vmSubscriptionReq);

        return request;
    }


    private static SubscriptionRequest createEstimatedTimetableSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion("2.0");

        EstimatedTimetableSubscriptionStructure etSubscriptionReq = new EstimatedTimetableSubscriptionStructure();
        etSubscriptionReq.setEstimatedTimetableRequest(etRequest);
        etSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        etSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        etSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        etSubscriptionReq.setChangeBeforeUpdates(createDataTypeFactory().newDuration(heartbeatInterval));

        request.getEstimatedTimetableSubscriptionRequests().add(etSubscriptionReq);

        return request;
    }


    private static SubscriptionRequest createProductionTimetableSubscriptionRequest(String requestorRef, String subscriptionId, String heartbeatInterval, String address, Duration subscriptionDuration) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address);

        ProductionTimetableRequestStructure etRequest = new ProductionTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion("2.0");

        ProductionTimetableSubscriptionRequest ptSubscriptionReq = new ProductionTimetableSubscriptionRequest();
        ptSubscriptionReq.setProductionTimetableRequest(etRequest);
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
        request.setAddress(address);
        request.setConsumerAddress(address);
        request.setRequestTimestamp(ZonedDateTime.now());
        logger.trace("Request timestamp set to {}.", request.getRequestTimestamp());

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

        Siri siri = new Siri();
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

    public static Siri createSXSiriObject(List<PtSituationElement> elements) {
        Siri siri = new Siri();
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

    public static Siri createVMSiriObject(List<VehicleActivityStructure> elements) {
        Siri siri = new Siri();
        ServiceDelivery delivery = new ServiceDelivery();
        VehicleMonitoringDeliveryStructure deliveryStructure = new VehicleMonitoringDeliveryStructure();
        deliveryStructure.getVehicleActivities().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getVehicleMonitoringDeliveries().add(deliveryStructure);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public static Siri createETSiriObject(List<EstimatedTimetableDeliveryStructure> elements) {
        Siri siri = new Siri();
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getEstimatedTimetableDeliveries().addAll(elements);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createPTSiriObject(List<ProductionTimetableDeliveryStructure> elements) {
        Siri siri = new Siri();
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getProductionTimetableDeliveries().addAll(elements);
        delivery.setResponseTimestamp(ZonedDateTime.now());
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public static MessageQualifierStructure randomMessageId() {
        return createMessageIdentifier(UUID.randomUUID().toString());
    }

    public static DatatypeFactory createDataTypeFactory() {
        try {
            return DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SubscriptionQualifierStructure randomSubscriptionId() {
        SubscriptionQualifierStructure subscriptionId = new SubscriptionQualifierStructure();
        subscriptionId.setValue(UUID.randomUUID().toString());
        return subscriptionId;
    }
}
