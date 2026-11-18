package no.rutebanken.anshar.routes.siri.helpers;

import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.org.siri.siri21.AdviceTypeEnumeration;
import uk.org.siri.siri21.AlertCauseEnumeration;
import uk.org.siri.siri21.CheckStatusRequestStructure;
import uk.org.siri.siri21.DatedVehicleJourneyRef;
import uk.org.siri.siri21.DefaultedTextStructure;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Extensions;
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

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static no.rutebanken.anshar.routes.siri.helpers.LargestAllocation.MB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The converter must give exactly what parsing the SIRI 2.1 XML text as SIRI 2.0 gives, without the text.
 */
class SiriVersionConverterTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-14T12:00:00+02:00");

    private static uk.org.siri.siri20.Siri viaText(Siri siri) throws Exception {
        return org.rutebanken.siri20.util.SiriXml.parseXml(SiriXml.toXml(siri));
    }

    private static void assertSameAsViaText(Siri siri) throws Exception {
        assertEquals(org.rutebanken.siri20.util.SiriXml.toXml(viaText(siri)),
                org.rutebanken.siri20.util.SiriXml.toXml(SiriVersionConverter.toSiri20(siri)));
    }

    private static Siri fixture(String path) throws Exception {
        try (InputStream in = new FileInputStream(path)) {
            return SiriXml.parseXml(in);
        }
    }

    @Test
    void etFixturesConvertAsViaText() throws Exception {
        assertSameAsViaText(fixture("src/test/resources/siri-et-gir-npe.xml"));
        assertSameAsViaText(fixture("src/test/resources/siri-et-from-bnr.xml"));
    }

    @Test
    void vmDeliveryConvertsAsViaText() throws Exception {
        VehicleActivityStructure activity = new VehicleActivityStructure();
        activity.setRecordedAtTime(NOW);
        activity.setValidUntilTime(NOW.plusMinutes(10));
        VehicleActivityStructure.MonitoredVehicleJourney journey = new VehicleActivityStructure.MonitoredVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        journey.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("1234");
        journey.setVehicleRef(vehicleRef);
        activity.setMonitoredVehicleJourney(journey);

        VehicleMonitoringDeliveryStructure delivery = new VehicleMonitoringDeliveryStructure();
        delivery.setVersion("2.1");
        delivery.setResponseTimestamp(NOW);
        delivery.getVehicleActivities().add(activity);
        Siri siri = createSiri();
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().add(delivery);

        assertSameAsViaText(siri);
    }

    @Test
    void sxDeliveryConvertsAsViaText() throws Exception {
        PtSituationElement situation = situation();
        situation.setAlertCause(AlertCauseEnumeration.ROADWORKS);
        PtAdviceStructure advice = new PtAdviceStructure();
        advice.setAdviceType(AdviceTypeEnumeration.USE_REPLACEMENT_BUS);
        PtConsequenceStructure consequence = new PtConsequenceStructure();
        consequence.setAdvice(advice);
        PtConsequencesStructure consequences = new PtConsequencesStructure();
        consequences.getConsequences().add(consequence);
        situation.setConsequences(consequences);

        assertSameAsViaText(sxDelivery(situation));
    }

    @Test
    void escapedAndNonAsciiTextIsKept() throws Exception {
        String text = "Buss & tog <innstilt> \"Østre\" Ålesund–Bodø 🚌";
        PtSituationElement situation = situation();
        situation.getSummaries().get(0).setValue(text);
        Siri siri = sxDelivery(situation);

        assertSameAsViaText(siri);
        assertEquals(text, firstSituation(SiriVersionConverter.toSiri20(siri)).getSummaries().get(0).getValue());
    }

    @Test
    void languageAttributesAreKept() throws Exception {
        PtSituationElement situation = situation();
        situation.getSummaries().get(0).setLang("NO");
        DefaultedTextStructure description = new DefaultedTextStructure();
        description.setValue("Description");
        description.setLang("EN");
        situation.getDescriptions().add(description);
        Siri siri = sxDelivery(situation);

        assertSameAsViaText(siri);
        assertEquals("NO", firstSituation(SiriVersionConverter.toSiri20(siri)).getSummaries().get(0).getLang());
    }

    @Test
    void extensionContentIsKept() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element custom = document.createElementNS("urn:anshar:test", "custom");
        custom.setTextContent("x");
        Extensions extensions = new Extensions();
        extensions.getAnies().add(custom);
        EstimatedVehicleJourney journey = journey("TST:ServiceJourney:1");
        journey.setExtensions(extensions);

        assertSameAsViaText(etDelivery(journey));
    }

    @Test
    void extensionWithNamesBuiltAtRunTimeIsKept() throws Exception {
        // Names not known when the code was compiled are not interned: the converter must cope with them
        String suffix = String.valueOf(System.nanoTime() % 10);
        String namespace = "urn:anshar:test:" + suffix;
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element custom = document.createElementNS(namespace, "t:custom" + suffix);
        custom.setAttributeNS(namespace, "t:attribute" + suffix, "value");
        custom.appendChild(document.createTextNode("before "));
        Element child = document.createElementNS(namespace, "t:child" + suffix);
        child.setTextContent("x");
        custom.appendChild(child);
        custom.appendChild(document.createTextNode(" after"));
        Extensions extensions = new Extensions();
        extensions.getAnies().add(custom);
        EstimatedVehicleJourney journey = journey("TST:ServiceJourney:1");
        journey.setExtensions(extensions);

        assertSameAsViaText(etDelivery(journey));
    }

    @Test
    void elementsOnlyInSiri21AreDropped() throws Exception {
        EstimatedVehicleJourney journey = journey("TST:ServiceJourney:1");
        VehicleOccupancyStructure occupancy = new VehicleOccupancyStructure();
        occupancy.setOccupancyLevel(OccupancyEnumeration.MANY_SEATS_AVAILABLE);
        journey.getEstimatedCalls().getEstimatedCalls().get(0).getExpectedDepartureOccupancies().add(occupancy);
        Siri siri = etDelivery(journey);

        assertSameAsViaText(siri);
        String xml = org.rutebanken.siri20.util.SiriXml.toXml(SiriVersionConverter.toSiri20(siri));
        assertFalse(xml.contains("ExpectedDepartureOccupancy"), xml);
        assertTrue(xml.contains("TST:ServiceJourney:1"), xml);
    }

    @Test
    void nonDeliveryAndEmptyDeliveryConvert() throws Exception {
        Siri checkStatus = new Siri();
        checkStatus.setVersion("2.1");
        CheckStatusRequestStructure request = new CheckStatusRequestStructure();
        request.setRequestTimestamp(NOW);
        checkStatus.setCheckStatusRequest(request);
        assertSameAsViaText(checkStatus);

        assertSameAsViaText(createSiri());
    }

    @Test
    void inputIsNotModified() throws Exception {
        Siri siri = fixture("src/test/resources/siri-et-from-bnr.xml");
        String before = SiriXml.toXml(siri);

        SiriVersionConverter.toSiri20(siri);

        assertEquals(before, SiriXml.toXml(siri));
    }

    @Test
    void concurrentConversionsAreIndependent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> conversions = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                Siri siri = etDelivery(journey("TST:ServiceJourney:" + thread));
                String expected = org.rutebanken.siri20.util.SiriXml.toXml(viaText(siri));
                conversions.add(() -> {
                    for (int i = 0; i < 50; i++) {
                        if (!expected.equals(org.rutebanken.siri20.util.SiriXml.toXml(SiriVersionConverter.toSiri20(siri)))) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            for (Future<Boolean> result : pool.invokeAll(conversions)) {
                assertTrue(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void allocatesNoLargeArraysUnlikeTheTextPath() throws Exception {
        Siri siri = largeEtDelivery();

        long textPath = LargestAllocation.of(() -> viaText(siri));
        long converter = LargestAllocation.of(() -> SiriVersionConverter.toSiri20(siri));

        // The text path holds the whole delivery as text: tens of megabytes in one array
        assertTrue(textPath >= 8 * MB, "largest allocation on the text path was only " + textPath + " bytes");
        assertTrue(converter < MB, "largest allocation by the converter was " + converter + " bytes");
    }

    @Test
    void largeConversionIsNoSlowerThanTheTextPath() throws Exception {
        Siri siri = largeEtDelivery();
        for (int i = 0; i < 3; i++) {
            viaText(siri);
            SiriVersionConverter.toSiri20(siri);
        }
        long textPath = Long.MAX_VALUE;
        long converter = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            viaText(siri);
            textPath = Math.min(textPath, System.nanoTime() - start);
            start = System.nanoTime();
            SiriVersionConverter.toSiri20(siri);
            converter = Math.min(converter, System.nanoTime() - start);
        }

        // Best of five each; 10% leeway for noise
        assertTrue(converter <= textPath * 1.1, "converter " + converter / 1_000_000 + " ms, text path " + textPath / 1_000_000 + " ms");
    }

    /** The BNR fixture's journeys repeated to 5,000, about 67 MB as XML text */
    private static Siri largeEtDelivery() throws Exception {
        Siri siri = fixture("src/test/resources/siri-et-from-bnr.xml");
        EstimatedVersionFrameStructure frame = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0);
        List<EstimatedVehicleJourney> journeys = new ArrayList<>(frame.getEstimatedVehicleJourneies());
        for (int i = 0; frame.getEstimatedVehicleJourneies().size() < 5_000; i++) {
            frame.getEstimatedVehicleJourneies().add(journeys.get(i % journeys.size()));
        }
        return siri;
    }

    private static uk.org.siri.siri20.PtSituationElement firstSituation(uk.org.siri.siri20.Siri siri) {
        return siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0);
    }

    private static Siri createSiri() {
        Siri siri = new Siri();
        siri.setVersion("2.1");
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.setResponseTimestamp(NOW);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    private static PtSituationElement situation() {
        PtSituationElement situation = new PtSituationElement();
        situation.setCreationTime(NOW);
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue("TST:SituationNumber:1");
        situation.setSituationNumber(situationNumber);
        DefaultedTextStructure summary = new DefaultedTextStructure();
        summary.setValue("Summary");
        situation.getSummaries().add(summary);
        return situation;
    }

    private static Siri sxDelivery(PtSituationElement situation) {
        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        delivery.setVersion("2.1");
        delivery.setResponseTimestamp(NOW);
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(situation);
        delivery.setSituations(situations);
        Siri siri = createSiri();
        siri.getServiceDelivery().getSituationExchangeDeliveries().add(delivery);
        return siri;
    }

    private static Siri etDelivery(EstimatedVehicleJourney... journeys) {
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

    private static EstimatedVehicleJourney journey(String datedVehicleJourneyRef) {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setRecordedAtTime(NOW);
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        journey.setLineRef(lineRef);
        DatedVehicleJourneyRef ref = new DatedVehicleJourneyRef();
        ref.setValue(datedVehicleJourneyRef);
        journey.setDatedVehicleJourneyRef(ref);
        journey.getVehicleModes().add(VehicleModesEnumeration.BUS);

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
