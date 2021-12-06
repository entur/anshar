package no.rutebanken.anshar.data;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultipleDatasetsFromSingleProviderTest extends SpringBootBaseTest {

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private Situations situations;

    @Autowired
    private SubscriptionManager manager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SiriObjectFactory factory;

    @BeforeEach
    public void init() {
        estimatedTimetables.clearAll();
        vehicleActivities.clearAll();
        situations.clearAll();
    }

    @Test
    public void testMultipleProvidersFromSingleVMDelivery() throws JAXBException {
        Siri siri = factory.createVMServiceDelivery(List.of(createVM("RUT"), createVM("RUT"), createVM("TST")));
        String xml = SiriXml.toXml(siri);

        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId("TST:1234-VM");
        subscriptionSetup.setDatasetId("VM-TST");
        subscriptionSetup.setSubscriptionType(SiriDataType.VEHICLE_MONITORING);

        manager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);

        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

        vehicleActivities.commitChanges();

        Collection all = vehicleActivities.getAll("VM-TST");
        assertEquals(3, all.size());
        Collection rut = vehicleActivities.getAll("RUT");
        assertEquals(0, rut.size());
        Collection tst = vehicleActivities.getAll("TST");
        assertEquals(0, tst.size());


        vehicleActivities.clearAll();

        //Testing the same data with different subscription-config
        subscriptionSetup.setUseProvidedCodespaceId(true);

        manager.updateSubscription(subscriptionSetup);

        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

        vehicleActivities.commitChanges();

        all = vehicleActivities.getAll("VM-TST");
        assertEquals(0, all.size());
        rut = vehicleActivities.getAll("RUT");
        assertEquals(2, rut.size());
        tst = vehicleActivities.getAll("TST");
        assertEquals(1, tst.size());

        manager.removeSubscription(subscriptionSetup.getSubscriptionId());
    }

    @Test
    public void testMultipleProvidersFromSingleETDelivery() throws JAXBException {
        Siri siri = factory.createETServiceDelivery(List.of(createET("RUT"), createET("RUT"), createET("TST")));
        String xml = SiriXml.toXml(siri);

        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId("TST:1234-ET");
        subscriptionSetup.setDatasetId("ET-TST");
        subscriptionSetup.setSubscriptionType(SiriDataType.ESTIMATED_TIMETABLE);
        manager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);


        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

        estimatedTimetables.commitChanges();

        Collection all = estimatedTimetables.getAll("ET-TST");
        assertEquals(3, all.size());
        Collection rut = estimatedTimetables.getAll("RUT");
        assertEquals(0, rut.size());
        Collection tst = estimatedTimetables.getAll("TST");
        assertEquals(0, tst.size());

        //Testing the same data with different subscription-config
        subscriptionSetup.setUseProvidedCodespaceId(true);
        manager.updateSubscription(subscriptionSetup);
        estimatedTimetables.clearAll();

        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

        estimatedTimetables.commitChanges();

        all = estimatedTimetables.getAll("ET-TST");
        assertEquals(0, all.size());
        rut = estimatedTimetables.getAll("RUT");
        assertEquals(2, rut.size());
        tst = estimatedTimetables.getAll("TST");
        assertEquals(1, tst.size());

        manager.removeSubscription(subscriptionSetup.getSubscriptionId());

    }

    @Test
    public void testMultipleProvidersFromSingleSXDelivery() throws JAXBException {
        Siri siri = factory.createSXServiceDelivery(List.of(createSX("RUT"), createSX("RUT"), createSX("TST")));
        String xml = SiriXml.toXml(siri);

        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId("TST:1234-SX");
        subscriptionSetup.setDatasetId("SX-TST");
        subscriptionSetup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);

        manager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);

        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

        situations.commitChanges();

        Collection<PtSituationElement> all = situations.getAll(subscriptionSetup.getDatasetId());
        assertEquals(3, all.size());
        Collection rutJourneys = situations.getAll("RUT");
        assertEquals(0, rutJourneys.size());
        Collection tstJourneys = situations.getAll("TST");
        assertEquals(0, tstJourneys.size());

        situations.clearAll();

        //Testing the same data with different subscription-config
        subscriptionSetup.setUseProvidedCodespaceId(true);
        manager.updateSubscription(subscriptionSetup);

        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));

        situations.commitChanges();

        all = situations.getAll(subscriptionSetup.getDatasetId());
        assertEquals(0, all.size());
        rutJourneys = situations.getAll("RUT");
        assertEquals(2, rutJourneys.size());
        tstJourneys = situations.getAll("TST");
        assertEquals(1, tstJourneys.size());
        manager.removeSubscription(subscriptionSetup.getSubscriptionId());
    }

    private PtSituationElement createSX(String codespaceId) {
        PtSituationElement situation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("TST:SituationNumber:" + (int)(Math.random()*10000));
        situation.setSituationNumber(sitNumber);
        RequestorRef participantRef = new RequestorRef();
        participantRef.setValue(codespaceId);
        situation.setParticipantRef(participantRef);

        HalfOpenTimestampOutputRangeStructure validity = new HalfOpenTimestampOutputRangeStructure();
        validity.setStartTime(ZonedDateTime.now().minusMinutes(5));
        validity.setEndTime(ZonedDateTime.now().plusMinutes(5));
        situation.getValidityPeriods().add(validity);

        return situation;
    }


    private VehicleActivityStructure createVM(String codespaceId) {
        VehicleActivityStructure element = new VehicleActivityStructure();

        element.setValidUntilTime(ZonedDateTime.now().plusMinutes(1));

        VehicleActivityStructure.MonitoredVehicleJourney mvj = new VehicleActivityStructure.MonitoredVehicleJourney();
        mvj.setDataSource(codespaceId);
        mvj.setLocationRecordedAtTime(ZonedDateTime.now().minusMinutes(1));
        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(1L));
        location.setLongitude(BigDecimal.valueOf(1L));
        mvj.setVehicleLocation(location);

        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("TST:Vehicle:" + (int)(Math.random()*10000));
        mvj.setVehicleRef(vehicleRef);

        element.setMonitoredVehicleJourney(mvj);
        return element;
    }

    private EstimatedVehicleJourney createET(String codespaceId) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1234");
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("TST:Vehicle:1234");
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(true);

        element.setDataSource(codespaceId);

        FramedVehicleJourneyRefStructure framedVehicleJourney = new FramedVehicleJourneyRefStructure();
        framedVehicleJourney.setDatedVehicleJourneyRef("2021-01-02");
        DataFrameRefStructure dataFrame = new DataFrameRefStructure();
        dataFrame.setValue("TST:ServiceJourney:" + UUID.randomUUID());
        framedVehicleJourney.setDataFrameRef(dataFrame);
        element.setFramedVehicleJourneyRef(framedVehicleJourney);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < 2; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(ZonedDateTime.now().plusMinutes(5));
            call.setExpectedArrivalTime(ZonedDateTime.now().plusMinutes(5));
            call.setAimedDepartureTime(ZonedDateTime.now().plusMinutes(6));
            call.setExpectedDepartureTime(ZonedDateTime.now().plusMinutes(6));
            call.setOrder(BigInteger.valueOf(i));
            call.setVisitNumber(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
