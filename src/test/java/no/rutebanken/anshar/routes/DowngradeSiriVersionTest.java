package no.rutebanken.anshar.routes;

import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.AdviceTypeEnumeration;
import uk.org.siri.siri21.AlertCauseEnumeration;
import uk.org.siri.siri21.CheckStatusRequestStructure;
import uk.org.siri.siri21.DatedVehicleJourneyRef;
import uk.org.siri.siri21.DefaultedTextStructure;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.OccupancyEnumeration;
import uk.org.siri.siri21.PtAdviceStructure;
import uk.org.siri.siri21.PtConsequenceStructure;
import uk.org.siri.siri21.PtConsequencesStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleOccupancyStructure;
import uk.org.siri.siri21.VehicleRef;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.ZonedDateTime;

import static no.rutebanken.anshar.routes.RestRouteBuilder.downgradeSiriVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that downgradeSiriVersion removes TAXI-journeys, silently ignores elements not supported in SIRI 2.0,
 * and never alters its input.
 */
public class DowngradeSiriVersionTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-14T12:00:00+02:00");

    @Test
    public void testEtFile() throws Exception {
        Siri siri;
        try (InputStream in = new FileInputStream("src/test/resources/siri-et-gir-npe.xml")) {
            siri = SiriXml.parseXml(in);
        }
        int journeyCount = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size();

        uk.org.siri.siri20.Siri downgraded = downgradeAndAssertInputUnchanged(siri);

        assertEquals("2.0", downgraded.getVersion());
        var delivery = downgraded.getServiceDelivery().getEstimatedTimetableDeliveries().get(0);
        assertEquals("2.0", delivery.getVersion());
        assertEquals(journeyCount, delivery.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size());
    }

    @Test
    public void testTaxiJourneysAreRemoved() throws Exception {
        Siri siri = createEtDelivery(
                createJourney("TST:ServiceJourney:1", VehicleModesEnumeration.BUS),
                createJourney("TST:ServiceJourney:2", VehicleModesEnumeration.TAXI),
                createJourney("TST:ServiceJourney:3", VehicleModesEnumeration.RAIL)
        );

        uk.org.siri.siri20.Siri downgraded = downgradeAndAssertInputUnchanged(siri);

        var journeys = downgraded.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies();
        assertEquals(2, journeys.size());
        assertEquals("TST:ServiceJourney:1", journeys.get(0).getDatedVehicleJourneyRef().getValue());
        assertEquals("TST:ServiceJourney:3", journeys.get(1).getDatedVehicleJourneyRef().getValue());
    }

    @Test
    public void testEtElementsOnlyInSiri21AreIgnored() throws Exception {
        EstimatedVehicleJourney journey = createJourney("TST:ServiceJourney:1", VehicleModesEnumeration.BUS);
        VehicleOccupancyStructure occupancy = new VehicleOccupancyStructure();
        occupancy.setOccupancyLevel(OccupancyEnumeration.MANY_SEATS_AVAILABLE);
        journey.getEstimatedCalls().getEstimatedCalls().get(0).getExpectedDepartureOccupancies().add(occupancy);

        uk.org.siri.siri20.Siri downgraded = downgradeAndAssertInputUnchanged(createEtDelivery(journey));

        String xml = org.rutebanken.siri20.util.SiriXml.toXml(downgraded);
        assertFalse(xml.contains("ExpectedDepartureOccupancy"), xml);
        assertTrue(xml.contains("TST:ServiceJourney:1"), xml);
    }

    @Test
    public void testVmDelivery() throws Exception {
        VehicleActivityStructure activity = new VehicleActivityStructure();
        activity.setRecordedAtTime(NOW);
        activity.setValidUntilTime(NOW.plusMinutes(10));
        VehicleActivityStructure.MonitoredVehicleJourney mvj = new VehicleActivityStructure.MonitoredVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        mvj.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("1234");
        mvj.setVehicleRef(vehicleRef);
        activity.setMonitoredVehicleJourney(mvj);

        VehicleMonitoringDeliveryStructure delivery = new VehicleMonitoringDeliveryStructure();
        delivery.setVersion("2.1");
        delivery.setResponseTimestamp(NOW);
        delivery.getVehicleActivities().add(activity);

        Siri siri = createSiri();
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().add(delivery);

        uk.org.siri.siri20.Siri downgraded = downgradeAndAssertInputUnchanged(siri);

        var downgradedDelivery = downgraded.getServiceDelivery().getVehicleMonitoringDeliveries().get(0);
        assertEquals("2.0", downgradedDelivery.getVersion());
        assertEquals("1234", downgradedDelivery.getVehicleActivities().get(0).getMonitoredVehicleJourney().getVehicleRef().getValue());
    }

    @Test
    public void testSxElementsOnlyInSiri21AreIgnoredAndEmptyAdviceRemoved() throws Exception {
        PtSituationElement situation = new PtSituationElement();
        situation.setCreationTime(NOW);
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue("TST:SituationNumber:1");
        situation.setSituationNumber(situationNumber);
        DefaultedTextStructure summary = new DefaultedTextStructure();
        summary.setValue("Summary");
        situation.getSummaries().add(summary);
        situation.setAlertCause(AlertCauseEnumeration.ROADWORKS);

        PtAdviceStructure advice = new PtAdviceStructure();
        advice.setAdviceType(AdviceTypeEnumeration.USE_REPLACEMENT_BUS);
        PtConsequenceStructure consequence = new PtConsequenceStructure();
        consequence.setAdvice(advice);
        PtConsequencesStructure consequences = new PtConsequencesStructure();
        consequences.getConsequences().add(consequence);
        situation.setConsequences(consequences);

        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        delivery.setVersion("2.1");
        delivery.setResponseTimestamp(NOW);
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(situation);
        delivery.setSituations(situations);

        Siri siri = createSiri();
        siri.getServiceDelivery().getSituationExchangeDeliveries().add(delivery);

        uk.org.siri.siri20.Siri downgraded = downgradeAndAssertInputUnchanged(siri);

        var downgradedSituation = downgraded.getServiceDelivery().getSituationExchangeDeliveries().get(0)
                .getSituations().getPtSituationElements().get(0);
        assertNull(downgradedSituation.getConsequences().getConsequences().get(0).getAdvice());
        assertFalse(org.rutebanken.siri20.util.SiriXml.toXml(downgraded).contains("AlertCause"));
    }

    @Test
    public void testNonDeliveryRequest() throws Exception {
        CheckStatusRequestStructure checkStatusRequest = new CheckStatusRequestStructure();
        checkStatusRequest.setRequestTimestamp(NOW);
        checkStatusRequest.setVersion("2.1");
        Siri siri = new Siri();
        siri.setVersion("2.1");
        siri.setCheckStatusRequest(checkStatusRequest);

        uk.org.siri.siri20.Siri downgraded = downgradeAndAssertInputUnchanged(siri);

        assertEquals("2.0", downgraded.getVersion());
        assertEquals(NOW.toInstant(), downgraded.getCheckStatusRequest().getRequestTimestamp().toInstant());
    }

    /**
     * The input may be shared (e.g. from the Hazelcast near cache), so downgrading must never alter it
     */
    private static uk.org.siri.siri20.Siri downgradeAndAssertInputUnchanged(Siri siri) throws Exception {
        String inputBefore = SiriXml.toXml(siri);

        uk.org.siri.siri20.Siri downgraded = downgradeSiriVersion(siri);

        assertEquals(inputBefore, SiriXml.toXml(siri), "Input was modified by downgradeSiriVersion");
        return downgraded;
    }

    private static Siri createSiri() {
        Siri siri = new Siri();
        siri.setVersion("2.1");
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.setResponseTimestamp(NOW);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    private static Siri createEtDelivery(EstimatedVehicleJourney... journeys) {
        EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
        frame.setRecordedAtTime(NOW);
        for (EstimatedVehicleJourney journey : journeys) {
            frame.getEstimatedVehicleJourneies().add(journey);
        }
        EstimatedTimetableDeliveryStructure delivery = new EstimatedTimetableDeliveryStructure();
        delivery.setVersion("2.1");
        delivery.setResponseTimestamp(NOW);
        delivery.getEstimatedJourneyVersionFrames().add(frame);

        Siri siri = createSiri();
        siri.getServiceDelivery().getEstimatedTimetableDeliveries().add(delivery);
        return siri;
    }

    private static EstimatedVehicleJourney createJourney(String datedVehicleJourneyRef, VehicleModesEnumeration mode) {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setRecordedAtTime(NOW);
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        journey.setLineRef(lineRef);
        DatedVehicleJourneyRef ref = new DatedVehicleJourneyRef();
        ref.setValue(datedVehicleJourneyRef);
        journey.setDatedVehicleJourneyRef(ref);
        journey.getVehicleModes().add(mode);

        EstimatedCall call = new EstimatedCall();
        StopPointRefStructure stopPointRef = new StopPointRefStructure();
        stopPointRef.setValue("NSR:Quay:1");
        call.setStopPointRef(stopPointRef);
        call.setOrder(BigInteger.ONE);
        call.setAimedDepartureTime(NOW);
        call.setExpectedDepartureTime(NOW.plusMinutes(2));
        EstimatedVehicleJourney.EstimatedCalls calls = new EstimatedVehicleJourney.EstimatedCalls();
        calls.getEstimatedCalls().add(call);
        journey.setEstimatedCalls(calls);
        return journey;
    }
}
