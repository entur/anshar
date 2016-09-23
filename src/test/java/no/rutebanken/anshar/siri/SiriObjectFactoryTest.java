package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;

public class SiriObjectFactoryTest {

    private int hoursUntilInitialTermination = 1;

    @Test
    public void testCreateVMSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest());
        List<VehicleMonitoringSubscriptionStructure> subscriptionRequests = vmSubscriptionRequest.getSubscriptionRequest().getVehicleMonitoringSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        VehicleMonitoringSubscriptionStructure subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

        ZonedDateTime initialTerminationTime = subscription.getInitialTerminationTime();

        assertTrue("Initial terminationtime has not been calculated correctly", ZonedDateTime.now().plusHours(hoursUntilInitialTermination).minusMinutes(1).isBefore(initialTerminationTime));
        assertTrue("Initial terminationtime has not been calculated correctly", ZonedDateTime.now().plusHours(hoursUntilInitialTermination).plusMinutes(1).isAfter(initialTerminationTime));

    }

    @Test
    public void testCreateVMServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(vmRequest.getSubscriptionRequest());

        assertNotNull(vmRequest.getServiceRequest());

        List<VehicleMonitoringRequestStructure> vmRequests = vmRequest.getServiceRequest().getVehicleMonitoringRequests();
        assertNotNull(vmRequests);

        assertTrue(vmRequests.size() == 1);

        VehicleMonitoringRequestStructure request = vmRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreateSXSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri sxSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(sxSubscriptionRequest.getSubscriptionRequest());

        List<SituationExchangeSubscriptionStructure> subscriptionRequests = sxSubscriptionRequest.getSubscriptionRequest().getSituationExchangeSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        SituationExchangeSubscriptionStructure subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

        ZonedDateTime initialTerminationTime = subscription.getInitialTerminationTime();

        assertTrue("Initial terminationtime has not been calculated correctly", ZonedDateTime.now().plusHours(hoursUntilInitialTermination).minusMinutes(1).isBefore(initialTerminationTime));
        assertTrue("Initial terminationtime has not been calculated correctly", ZonedDateTime.now().plusHours(hoursUntilInitialTermination).plusMinutes(1).isAfter(initialTerminationTime));



    }

    @Test
    public void testCreateSXServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri sxRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(sxRequest.getSubscriptionRequest());

        assertNotNull(sxRequest.getServiceRequest());

        List<SituationExchangeRequestStructure> sxRequests = sxRequest.getServiceRequest().getSituationExchangeRequests();
        assertNotNull(sxRequests);

        assertTrue(sxRequests.size() == 1);

        SituationExchangeRequestStructure request = sxRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreateETSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest());

        List<EstimatedTimetableSubscriptionStructure> subscriptionRequests = vmSubscriptionRequest.getSubscriptionRequest().getEstimatedTimetableSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        EstimatedTimetableSubscriptionStructure subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

        ZonedDateTime initialTerminationTime = subscription.getInitialTerminationTime();

        assertTrue("Initial terminationtime has not been calculated correctly", ZonedDateTime.now().plusHours(hoursUntilInitialTermination).minusMinutes(1).isBefore(initialTerminationTime));
        assertTrue("Initial terminationtime has not been calculated correctly", ZonedDateTime.now().plusHours(hoursUntilInitialTermination).plusMinutes(1).isAfter(initialTerminationTime));


    }

    @Test
    public void testCreateETServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri etRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(etRequest.getSubscriptionRequest());

        assertNotNull(etRequest.getServiceRequest());

        List<EstimatedTimetableRequestStructure> etRequests = etRequest.getServiceRequest().getEstimatedTimetableRequests();
        assertNotNull(etRequests);

        assertTrue(etRequests.size() == 1);

        EstimatedTimetableRequestStructure request = etRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreatePTSubscription(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri vmSubscriptionRequest = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
        assertNotNull(vmSubscriptionRequest.getSubscriptionRequest());

        List<ProductionTimetableSubscriptionRequest> subscriptionRequests = vmSubscriptionRequest.getSubscriptionRequest().getProductionTimetableSubscriptionRequests();
        assertNotNull(subscriptionRequests);

        assertTrue(subscriptionRequests.size() == 1);

        ProductionTimetableSubscriptionRequest subscription = subscriptionRequests.get(0);
        assertNotNull(subscription.getSubscriptionIdentifier());
        assertNotNull(subscription.getSubscriptionIdentifier().getValue());
        assertEquals(subscriptionSetup.getSubscriptionId(), subscription.getSubscriptionIdentifier().getValue());

    }

    @Test
    public void testCreatePTServiceRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri ptRequest = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        assertNull(ptRequest.getSubscriptionRequest());

        assertNotNull(ptRequest.getServiceRequest());

        List<ProductionTimetableRequestStructure> ptRequests = ptRequest.getServiceRequest().getProductionTimetableRequests();
        assertNotNull(ptRequests);

        assertTrue(ptRequests.size() == 1);

        ProductionTimetableRequestStructure request = ptRequests.get(0);
        assertNotNull(request);
    }

    @Test
    public void testCreateTerminateSubscriptionRequest(){

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        Siri request = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);
        assertNotNull(request);
    }

    @Test
    public void testCreateNullTerminateSubscriptionRequest(){

        Siri request = SiriObjectFactory.createTerminateSubscriptionRequest(null);
        assertNull(request);

        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                UUID.randomUUID().toString());

        request = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);
        assertNotNull(request);

    }

    private SubscriptionSetup createSubscriptionSetup(SubscriptionSetup.SubscriptionType type, SubscriptionSetup.SubscriptionMode mode, String subscriptionId) {
        return createSubscriptionSetup(type, mode, subscriptionId, "RutebankenDev");
    }

    private SubscriptionSetup createSubscriptionSetup(SubscriptionSetup.SubscriptionType type, SubscriptionSetup.SubscriptionMode mode, String subscriptionId, String requestorRef) {
        return new SubscriptionSetup(
                type,
                mode,
            "http://localhost",
            Duration.ofMinutes(1),
            "http://www.kolumbus.no/siri",
            new HashMap<>(),
            "1.4",
            "dummyvm",
            "dum",
            SubscriptionSetup.ServiceType.SOAP,
            new ArrayList<>(),
            subscriptionId,
            requestorRef,
            Duration.ofHours(hoursUntilInitialTermination),
            true
            );
    }
}
