package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.avro.AvroConvertorProcessor;
import no.rutebanken.anshar.routes.pubsub.SiriAvroJsonSerializer;
import org.entur.avro.realtime.siri.converter.Converter;
import org.entur.avro.realtime.siri.model.EstimatedVehicleJourneyRecord;
import org.entur.avro.realtime.siri.model.PtSituationElementRecord;
import org.entur.avro.realtime.siri.model.SiriRecord;
import org.entur.siri21.util.SiriXml;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static no.rutebanken.anshar.routes.kafka.KafkaConfig.CODESPACE_ID_KAFKA_HEADER_NAME;

/**
 * Builds the messages published to the outbound topics for each element of a delivery whose IDs are already mapped:
 * one {@code Siri} per ET journey or SX situation, wrapped as {@code xsl/split.xsl} wraps it, as XML and as Avro JSON.
 */
public final class SiriTopicMessages {

    private static final String SIRI_VERSION = "2.0";
    static final String BREADCRUMB_ATTRIBUTE = "breadcrumbId";

    /** The messages for one element, or why they could not be built */
    public sealed interface Result permits Converted, Failed {
    }

    public record Converted(ElementMessages messages) implements Result {
    }

    public record Failed(String elementId, Exception error) implements Result {
    }

    private SiriTopicMessages() {
    }

    public static List<Result> forEstimatedTimetables(Siri mappedDelivery, String codespace, String breadcrumbId, long storedAt) {
        return forEstimatedTimetables(mappedDelivery, codespace, breadcrumbId, storedAt, Converter::jaxb2Avro);
    }

    static List<Result> forEstimatedTimetables(Siri mappedDelivery, String codespace, String breadcrumbId, long storedAt,
                                               Function<Siri, SiriRecord> toAvro) {
        List<Result> results = new ArrayList<>();
        if (mappedDelivery == null || mappedDelivery.getServiceDelivery() == null) {
            return results;
        }
        ServiceDelivery serviceDelivery = mappedDelivery.getServiceDelivery();
        for (EstimatedTimetableDeliveryStructure delivery : serviceDelivery.getEstimatedTimetableDeliveries()) {
            for (EstimatedVersionFrameStructure frame : delivery.getEstimatedJourneyVersionFrames()) {
                for (EstimatedVehicleJourney journey : frame.getEstimatedVehicleJourneies()) {
                    String id = elementId(journey);
                    try {
                        Siri single = singleJourney(serviceDelivery, delivery, frame, journey);
                        EstimatedVehicleJourneyRecord record = toAvro.apply(single).getServiceDelivery()
                                .getEstimatedTimetableDeliveries().get(0)
                                .getEstimatedJourneyVersionFrames().get(0)
                                .getEstimatedVehicleJourneys().get(0);
                        results.add(new Converted(new ElementMessages(id,
                                xmlMessage(single, codespace, breadcrumbId, storedAt),
                                avroMessage(SiriAvroJsonSerializer.toJson(record)),
                                record,
                                AvroConvertorProcessor.createHeaders(record, codespace))));
                    } catch (Exception e) {
                        results.add(new Failed(id, e));
                    }
                }
            }
        }
        return results;
    }

    public static List<Result> forSituations(Siri mappedDelivery, String codespace, String breadcrumbId, long storedAt) {
        List<Result> results = new ArrayList<>();
        if (mappedDelivery == null || mappedDelivery.getServiceDelivery() == null) {
            return results;
        }
        ServiceDelivery serviceDelivery = mappedDelivery.getServiceDelivery();
        for (SituationExchangeDeliveryStructure delivery : serviceDelivery.getSituationExchangeDeliveries()) {
            if (delivery.getSituations() == null) {
                continue;
            }
            for (PtSituationElement situation : delivery.getSituations().getPtSituationElements()) {
                String id = situation.getSituationNumber() != null ? situation.getSituationNumber().getValue() : null;
                try {
                    Siri single = singleSituation(serviceDelivery, delivery, situation);
                    PtSituationElementRecord record = Converter.jaxb2Avro(single).getServiceDelivery()
                            .getSituationExchangeDeliveries().get(0)
                            .getSituations().get(0);
                    results.add(new Converted(new ElementMessages(id,
                            xmlMessage(single, codespace, breadcrumbId, storedAt),
                            avroMessage(SiriAvroJsonSerializer.toJson(record)),
                            record,
                            AvroConvertorProcessor.createHeaders(record, codespace))));
                } catch (Exception e) {
                    results.add(new Failed(id, e));
                }
            }
        }
        return results;
    }

    /** The id the journey is published under, as the Kafka key resolves it */
    static String elementId(EstimatedVehicleJourney journey) {
        if (journey.getFramedVehicleJourneyRef() != null) {
            return journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        }
        if (journey.getDatedVehicleJourneyRef() != null) {
            return journey.getDatedVehicleJourneyRef().getValue();
        }
        return journey.getEstimatedVehicleJourneyCode();
    }

    private static Siri singleJourney(ServiceDelivery serviceDelivery, EstimatedTimetableDeliveryStructure delivery,
                                      EstimatedVersionFrameStructure frame, EstimatedVehicleJourney journey) {
        EstimatedVersionFrameStructure singleFrame = new EstimatedVersionFrameStructure();
        singleFrame.setRecordedAtTime(frame.getRecordedAtTime());
        singleFrame.getEstimatedVehicleJourneies().add(journey);

        EstimatedTimetableDeliveryStructure singleDelivery = new EstimatedTimetableDeliveryStructure();
        singleDelivery.setVersion(SIRI_VERSION);
        singleDelivery.setResponseTimestamp(delivery.getResponseTimestamp());
        singleDelivery.getEstimatedJourneyVersionFrames().add(singleFrame);

        ServiceDelivery single = envelope(serviceDelivery);
        single.getEstimatedTimetableDeliveries().add(singleDelivery);
        return siri(single);
    }

    private static Siri singleSituation(ServiceDelivery serviceDelivery, SituationExchangeDeliveryStructure delivery,
                                        PtSituationElement situation) {
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(situation);

        SituationExchangeDeliveryStructure singleDelivery = new SituationExchangeDeliveryStructure();
        singleDelivery.setVersion(SIRI_VERSION);
        singleDelivery.setResponseTimestamp(delivery.getResponseTimestamp());
        singleDelivery.setSituations(situations);

        ServiceDelivery single = envelope(serviceDelivery);
        single.getSituationExchangeDeliveries().add(singleDelivery);
        return siri(single);
    }

    /** What split.xsl copies from the delivery's ServiceDelivery */
    private static ServiceDelivery envelope(ServiceDelivery serviceDelivery) {
        ServiceDelivery single = new ServiceDelivery();
        single.setResponseTimestamp(serviceDelivery.getResponseTimestamp());
        single.setProducerRef(serviceDelivery.getProducerRef());
        return single;
    }

    private static Siri siri(ServiceDelivery serviceDelivery) {
        Siri siri = new Siri();
        siri.setVersion(SIRI_VERSION);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    private static PubsubMessage xmlMessage(Siri single, String codespace, String breadcrumbId, long storedAt) throws Exception {
        PubsubMessage.Builder message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(SiriXml.toXml(single)))
                .putAttributes(CODESPACE_ID_KAFKA_HEADER_NAME, codespace)
                .putAttributes(PrometheusMetricsService.STORED_AT_HEADER, String.valueOf(storedAt));
        if (breadcrumbId != null) {
            message.putAttributes(BREADCRUMB_ATTRIBUTE, breadcrumbId);
        }
        return message.build();
    }

    private static PubsubMessage avroMessage(String json) {
        return PubsubMessage.newBuilder().setData(ByteString.copyFrom(json, StandardCharsets.UTF_8)).build();
    }
}
