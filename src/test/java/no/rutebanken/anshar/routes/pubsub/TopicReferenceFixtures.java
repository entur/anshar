package no.rutebanken.anshar.routes.pubsub;

import org.entur.siri21.util.SiriXml;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed ET and SX deliveries, as {@code ServerSubscriptionManager} hands them to the outbound topic routes,
 * with fixed timestamps so the messages published for them can be compared across implementations.
 * Every call builds new objects, since the routes map IDs in place.
 */
public final class TopicReferenceFixtures {

    public static final ZonedDateTime TIMESTAMP = ZonedDateTime.parse("2026-11-17T12:00:00+01:00");
    public static final String PRODUCER_REF = "ENT";
    public static final String ET_CODESPACE = "BNR";
    public static final String SX_CODESPACE = "ENT";
    public static final long STORED_AT = 1794920400000L;

    /** Number of journeys or situations in each delivery */
    public static final int[] ET_DELIVERY_SIZES = {1, 2, 5};
    public static final int[] SX_DELIVERY_SIZES = {1, 3};

    private TopicReferenceFixtures() {
    }

    public static List<Siri> etDeliveries() {
        List<EstimatedVehicleJourney> journeys = bnrJourneys();
        List<Siri> deliveries = new ArrayList<>();
        int next = 0;
        for (int size : ET_DELIVERY_SIZES) {
            deliveries.add(etDelivery(journeys.subList(next, next + size)));
            next += size;
        }
        return deliveries;
    }

    public static List<Siri> sxDeliveries() {
        List<Siri> deliveries = new ArrayList<>();
        int next = 1;
        for (int size : SX_DELIVERY_SIZES) {
            deliveries.add(sxDelivery(next, size));
            next += size;
        }
        return deliveries;
    }

    /** The journeys of the Bane NOR test delivery, parsed anew on every call */
    public static List<EstimatedVehicleJourney> bnrJourneys() {
        try (InputStream xml = TopicReferenceFixtures.class.getResourceAsStream("/siri-et-from-bnr.xml")) {
            Siri siri = SiriXml.parseXml(xml);
            return new ArrayList<>(siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                    .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read siri-et-from-bnr.xml", e);
        }
    }

    /** The delivery {@code SiriObjectFactory.createETServiceDelivery} builds, with fixed timestamps */
    public static Siri etDelivery(List<EstimatedVehicleJourney> journeys) {
        EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
        frame.setRecordedAtTime(TIMESTAMP);
        frame.getEstimatedVehicleJourneies().addAll(journeys);

        EstimatedTimetableDeliveryStructure etDelivery = new EstimatedTimetableDeliveryStructure();
        etDelivery.setVersion("2.0");
        etDelivery.setResponseTimestamp(TIMESTAMP);
        etDelivery.getEstimatedJourneyVersionFrames().add(frame);

        ServiceDelivery serviceDelivery = serviceDelivery();
        serviceDelivery.getEstimatedTimetableDeliveries().add(etDelivery);
        return siri(serviceDelivery);
    }

    private static Siri sxDelivery(int firstNumber, int situations) {
        StringBuilder xml = new StringBuilder("""
                <Siri xmlns="http://www.siri.org.uk/siri" version="2.0"><ServiceDelivery>
                <ResponseTimestamp>2026-11-17T12:00:00+01:00</ResponseTimestamp><ProducerRef>ENT</ProducerRef>
                <SituationExchangeDelivery version="2.0"><ResponseTimestamp>2026-11-17T12:00:00+01:00</ResponseTimestamp>
                <Situations>""");
        for (int number = firstNumber; number < firstNumber + situations; number++) {
            xml.append("""
                    <PtSituationElement>
                      <CreationTime>2026-11-17T11:00:00+01:00</CreationTime>
                      <ParticipantRef>ENT</ParticipantRef>
                      <SituationNumber>ENT:SituationNumber:%d</SituationNumber>
                      <Source><SourceType>directReport</SourceType></Source>
                      <Progress>open</Progress>
                      <ValidityPeriod><StartTime>2026-11-17T11:00:00+01:00</StartTime><EndTime>2026-11-18T11:00:00+01:00</EndTime></ValidityPeriod>
                      <UndefinedReason/>
                      <ReportType>incident</ReportType>
                      <Summary xml:lang="NO">Avvik %d</Summary>
                      <Description xml:lang="NO">Beskrivelse av avvik %d</Description>
                      <Affects><Networks><AffectedNetwork><AffectedLine><LineRef>ENT:Line:%d</LineRef></AffectedLine></AffectedNetwork></Networks></Affects>
                    </PtSituationElement>""".formatted(number, number, number, number));
        }
        xml.append("</Situations></SituationExchangeDelivery></ServiceDelivery></Siri>");
        try {
            return SiriXml.parseXml(new java.io.ByteArrayInputStream(xml.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid SX fixture", e);
        }
    }

    private static ServiceDelivery serviceDelivery() {
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.setResponseTimestamp(TIMESTAMP);
        RequestorRef producerRef = new RequestorRef();
        producerRef.setValue(PRODUCER_REF);
        serviceDelivery.setProducerRef(producerRef);
        return serviceDelivery;
    }

    private static Siri siri(ServiceDelivery serviceDelivery) {
        Siri siri = new Siri();
        siri.setVersion("2.0");
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}
