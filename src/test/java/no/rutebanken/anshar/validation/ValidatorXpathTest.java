package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.et.RecordedActualArrivalTimeValidator;
import no.rutebanken.anshar.routes.validation.validators.et.RecordedActualDepartureTimeValidator;
import no.rutebanken.anshar.routes.validation.validators.et.RecordedAimedArrivalTimeValidator;
import no.rutebanken.anshar.routes.validation.validators.fm.FacilityRefValidator;
import no.rutebanken.anshar.routes.validation.validators.fm.FmValidityPeriodValidator;
import no.rutebanken.anshar.routes.validation.validators.sx.AccessibilityAssessmentValidator;
import no.rutebanken.anshar.util.SecureXmlFactories;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import uk.org.acbs.siri21.AccessibilityAssessmentStructure;
import uk.org.siri.siri21.AffectedStopPlaceStructure;
import uk.org.siri.siri21.AffectsScopeStructure;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.FacilityMonitoringDeliveryStructure;
import uk.org.siri.siri21.FacilityRef;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The validator unit-tests call isValid() on hand-built nodes, so they never exercise getXpath().
 * These tests marshal real SIRI 2.1 objects and verify that each validator's xpath finds the element
 * it is meant to validate - the same way SiriXmlValidator selects nodes.
 */
public class ValidatorXpathTest {

    @Test
    public void testXpathHarnessFindsKnownWorkingRule() throws Exception {
        assertEquals(1, countMatches(new RecordedAimedArrivalTimeValidator(), createEtDelivery()));
    }

    @Test
    public void testRecordedActualArrivalTimeXpathMatchesRecordedCall() throws Exception {
        assertEquals(1, countMatches(new RecordedActualArrivalTimeValidator(), createEtDelivery()));
    }

    @Test
    public void testRecordedActualDepartureTimeXpathMatchesRecordedCall() throws Exception {
        assertEquals(1, countMatches(new RecordedActualDepartureTimeValidator(), createEtDelivery()));
    }

    @Test
    public void testAccessibilityAssessmentXpathMatchesAffectedStopPlace() throws Exception {
        assertEquals(1, countMatches(new AccessibilityAssessmentValidator(), createSxDelivery()));
    }

    @Test
    public void testFacilityRefXpathMatchesFacilityCondition() throws Exception {
        assertEquals(1, countMatches(new FacilityRefValidator(), createFmDelivery()));
    }

    @Test
    public void testFmValidityPeriodXpathMatchesFacilityCondition() throws Exception {
        assertEquals(1, countMatches(new FmValidityPeriodValidator(), createFmDelivery()));
    }

    private int countMatches(CustomValidator validator, Siri siri) throws Exception {
        String xml = SiriXml.toXml(siri);
        Document document = SecureXmlFactories.secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                .evaluate(validator.getXpath(), document, XPathConstants.NODESET);
        return nodes.getLength();
    }

    private Siri createEtDelivery() {
        RecordedCall call = new RecordedCall();
        ZonedDateTime time = ZonedDateTime.now();
        call.setAimedArrivalTime(time);
        call.setAimedDepartureTime(time);
        call.setActualArrivalTime(time);
        call.setActualDepartureTime(time);

        EstimatedVehicleJourney.RecordedCalls recordedCalls = new EstimatedVehicleJourney.RecordedCalls();
        recordedCalls.getRecordedCalls().add(call);

        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setRecordedCalls(recordedCalls);

        EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
        frame.getEstimatedVehicleJourneies().add(journey);

        EstimatedTimetableDeliveryStructure delivery = new EstimatedTimetableDeliveryStructure();
        delivery.getEstimatedJourneyVersionFrames().add(frame);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getEstimatedTimetableDeliveries().add(delivery);
        return wrap(serviceDelivery);
    }

    private Siri createSxDelivery() {
        AffectedStopPlaceStructure stopPlace = new AffectedStopPlaceStructure();
        stopPlace.setAccessibilityAssessment(new AccessibilityAssessmentStructure());

        AffectsScopeStructure.StopPlaces stopPlaces = new AffectsScopeStructure.StopPlaces();
        stopPlaces.getAffectedStopPlaces().add(stopPlace);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        affects.setStopPlaces(stopPlaces);

        PtSituationElement situation = new PtSituationElement();
        situation.setAffects(affects);

        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(situation);

        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        delivery.setSituations(situations);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getSituationExchangeDeliveries().add(delivery);
        return wrap(serviceDelivery);
    }

    private Siri createFmDelivery() {
        FacilityRef facilityRef = new FacilityRef();
        facilityRef.setValue("TST:Parking:1");

        HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();
        validityPeriod.setStartTime(ZonedDateTime.now());

        FacilityConditionStructure condition = new FacilityConditionStructure();
        condition.setFacilityRef(facilityRef);
        condition.setValidityPeriod(validityPeriod);

        FacilityMonitoringDeliveryStructure delivery = new FacilityMonitoringDeliveryStructure();
        delivery.getFacilityConditions().add(condition);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getFacilityMonitoringDeliveries().add(delivery);
        return wrap(serviceDelivery);
    }

    private Siri wrap(ServiceDelivery serviceDelivery) {
        Siri siri = new Siri();
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}
