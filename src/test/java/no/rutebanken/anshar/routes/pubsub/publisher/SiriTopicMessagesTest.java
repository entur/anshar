package no.rutebanken.anshar.routes.pubsub.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.pubsub.v1.PubsubMessage;
import no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.component.kafka.KafkaConstants;
import org.entur.avro.realtime.siri.converter.Converter;
import org.entur.avro.realtime.siri.model.SiriRecord;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.Siri;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures.ET_CODESPACE;
import static no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures.STORED_AT;
import static no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures.SX_CODESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messages built for each element must match what the Camel topic routes published for the same deliveries,
 * as recorded by {@code TopicReferenceCaptureKT} in src/test/resources/topic-reference.
 */
class SiriTopicMessagesTest {

    private static final String BREADCRUMB = "4A3C-0000000000000001";
    // The element id in XML (<DatedVehicleJourneyRef>id<) and in Avro JSON ("datedVehicleJourneyRef":{"string":"id"), as the capture reads it
    private static final Pattern ET_ID = Pattern.compile("(?i)datedVehicleJourneyRef\\W+(?:string\\W+)?([^<\"]+)");
    private static final Pattern SX_ID = Pattern.compile("(?i)situationNumber\\W+(?:string\\W+)?([^<\"]+)");

    private final ObjectMapper json = new ObjectMapper();
    private final MappingAdapterPresets presets = new MappingAdapterPresets(null);

    private Siri mapped(Siri delivery) {
        // As direct:siri.transform.data did before the capture
        return SiriValueTransformer.transform(delivery, presets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT), true, false);
    }

    private List<List<ElementMessages>> et() {
        return build(TopicReferenceFixtures.etDeliveries(), ET_CODESPACE, SiriTopicMessages::forEstimatedTimetables);
    }

    private List<List<ElementMessages>> sx() {
        return build(TopicReferenceFixtures.sxDeliveries(), SX_CODESPACE, SiriTopicMessages::forSituations);
    }

    private interface Builder {
        List<ElementMessages> build(Siri delivery, String codespace, String breadcrumbId, long storedAt);
    }

    private List<List<ElementMessages>> build(List<Siri> deliveries, String codespace, Builder builder) {
        List<List<ElementMessages>> converted = new ArrayList<>();
        for (Siri delivery : deliveries) {
            List<ElementMessages> messages = builder.build(mapped(delivery), codespace, BREADCRUMB, STORED_AT);
            for (ElementMessages element : messages) {
                assertNotNull(element.xml(), () -> "no XML for " + element.elementId() + ": " + element.xmlError());
                assertNotNull(element.avro(), () -> "no Avro for " + element.elementId() + ": " + element.avroError());
            }
            converted.add(messages);
        }
        return converted;
    }

    /** Reference entries by delivery, then by element id */
    private List<Map<String, JsonNode>> reference(String file, Pattern idPattern) throws Exception {
        List<Map<String, JsonNode>> deliveries = new ArrayList<>();
        for (JsonNode entry : json.readTree(getClass().getResourceAsStream("/topic-reference/" + file))) {
            int delivery = entry.get("delivery").asInt();
            while (deliveries.size() <= delivery) {
                deliveries.add(new HashMap<>());
            }
            deliveries.get(delivery).put(elementId(entry.get("data").asText(), idPattern), entry);
        }
        return deliveries;
    }

    private static String elementId(String data, Pattern idPattern) {
        Matcher matcher = idPattern.matcher(data);
        while (matcher.find()) {
            if (!matcher.group(1).startsWith("null")) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("No element id in " + data);
    }

    private static String text(PubsubMessage message) {
        return message.getData().toString(StandardCharsets.UTF_8);
    }

    private static String normalizedXml(String xml) throws Exception {
        return SiriXml.toXml(SiriXml.parseXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    private void assertMatchesReference(List<List<ElementMessages>> built, String file, Pattern idPattern,
                                        BiFunction<ElementMessages, JsonNode, Void> compare) throws Exception {
        List<Map<String, JsonNode>> reference = reference(file, idPattern);
        assertEquals(reference.size(), built.size(), "deliveries");
        for (int delivery = 0; delivery < built.size(); delivery++) {
            assertEquals(reference.get(delivery).size(), built.get(delivery).size(), "messages in delivery " + delivery);
            for (ElementMessages element : built.get(delivery)) {
                String id = elementId(text(element.xml()), idPattern);
                JsonNode expected = reference.get(delivery).get(id);
                assertNotNull(expected, "no reference message for " + id + " in delivery " + delivery);
                compare.apply(element, expected);
            }
        }
    }

    private Void compareXml(ElementMessages element, JsonNode expected) {
        PubsubMessage xml = element.xml();
        try {
            assertEquals(normalizedXml(expected.get("data").asText()), normalizedXml(text(xml)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Map<String, String> attributes = new HashMap<>(xml.getAttributesMap());
        assertEquals(BREADCRUMB, attributes.remove("breadcrumbId"));
        Map<String, String> expectedAttributes = json.convertValue(expected.get("attributes"), Map.class);
        expectedAttributes.remove("breadcrumbId");
        assertEquals(expectedAttributes, attributes);
        return null;
    }

    private Void compareAvro(ElementMessages element, JsonNode expected) {
        PubsubMessage avro = element.avro().json();
        assertEquals(expected.get("data").asText(), text(avro));
        assertTrue(avro.getAttributesMap().isEmpty(), "Avro attributes " + avro.getAttributesMap());
        return null;
    }

    @Test
    void etXmlMatchesReference() throws Exception {
        assertMatchesReference(et(), "et-xml.json", ET_ID, this::compareXml);
    }

    @Test
    void etAvroJsonMatchesReferenceByteForByte() throws Exception {
        assertMatchesReference(et(), "et-avro.json", ET_ID, this::compareAvro);
    }

    @Test
    void sxXmlMatchesReference() throws Exception {
        assertMatchesReference(sx(), "sx-xml.json", SX_ID, this::compareXml);
    }

    @Test
    void sxAvroJsonMatchesReferenceByteForByte() throws Exception {
        assertMatchesReference(sx(), "sx-avro.json", SX_ID, this::compareAvro);
    }

    @Test
    void oneMessagePerElementInDeliveryOrder() {
        List<EstimatedVehicleJourney> journeys = TopicReferenceFixtures.bnrJourneys().subList(0, 5);
        List<String> expectedIds = journeys.stream().map(SiriTopicMessagesTest::id).toList();

        List<ElementMessages> results = SiriTopicMessages.forEstimatedTimetables(
                mapped(TopicReferenceFixtures.etDelivery(journeys)), ET_CODESPACE, BREADCRUMB, STORED_AT);

        assertEquals(expectedIds, results.stream().map(ElementMessages::elementId).toList());
    }

    private static String id(EstimatedVehicleJourney journey) {
        if (journey.getFramedVehicleJourneyRef() != null) {
            return journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        }
        return journey.getDatedVehicleJourneyRef().getValue();
    }

    @Test
    void noOrderingKey() {
        for (List<ElementMessages> delivery : et()) {
            for (ElementMessages element : delivery) {
                assertEquals("", element.xml().getOrderingKey());
                assertEquals("", element.avro().json().getOrderingKey());
            }
        }
    }

    @Test
    void etKafkaHeaders() {
        ElementMessages first = et().get(0).get(0);
        Map<String, Object> headers = first.avro().kafkaHeaders();
        assertEquals(ET_CODESPACE, headers.get("codespaceId"));
        assertTrue(String.valueOf(headers.get(KafkaConstants.KEY)).startsWith(first.elementId()),
                "Kafka key " + headers.get(KafkaConstants.KEY));
        assertTrue(headers.containsKey("lineRef"));
    }

    @Test
    void sxKafkaHeaders() {
        ElementMessages first = sx().get(0).get(0);
        assertEquals(Map.of(KafkaConstants.KEY, "ENT:SituationNumber:1", "codespaceId", SX_CODESPACE),
                first.avro().kafkaHeaders());
    }

    @Test
    void avroFailureLeavesTheXmlMessageAndOtherElements() {
        List<EstimatedVehicleJourney> journeys = new ArrayList<>(TopicReferenceFixtures.bnrJourneys().subList(0, 3));
        String brokenId = id(journeys.get(1));
        // The Avro converter accepts any journey we can build, so the failure is injected
        Function<Siri, SiriRecord> failsForBroken = single -> {
            EstimatedVehicleJourney journey = single.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                    .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0);
            if (id(journey).equals(brokenId)) {
                throw new IllegalStateException("Unable to convert " + brokenId);
            }
            return Converter.jaxb2Avro(single);
        };

        List<ElementMessages> results = SiriTopicMessages.forEstimatedTimetables(
                mapped(TopicReferenceFixtures.etDelivery(journeys)), ET_CODESPACE, BREADCRUMB, STORED_AT, failsForBroken);

        assertEquals(3, results.size());
        ElementMessages broken = results.get(1);
        assertEquals(brokenId, broken.elementId());
        assertNotNull(broken.xml());
        assertNull(broken.avro());
        assertNotNull(broken.avroError());
        assertNotNull(results.get(0).avro());
        assertNotNull(results.get(2).avro());
    }

    @Test
    void journeyWithoutLineRefIsStillPublishedEverywhere() {
        List<EstimatedVehicleJourney> journeys = new ArrayList<>(TopicReferenceFixtures.bnrJourneys().subList(0, 1));
        journeys.get(0).setLineRef(null);

        ElementMessages element = SiriTopicMessages.forEstimatedTimetables(
                mapped(TopicReferenceFixtures.etDelivery(journeys)), ET_CODESPACE, BREADCRUMB, STORED_AT).get(0);

        assertNotNull(element.xml());
        assertNotNull(element.avro(), () -> "Avro failed: " + element.avroError());
        assertEquals("", element.avro().kafkaHeaders().get("lineRef"));
    }

    @Test
    void situationWithoutNumberIsStillPublishedEverywhere() {
        Siri delivery = TopicReferenceFixtures.sxDeliveries().get(0);
        delivery.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0)
                .setSituationNumber(null);

        ElementMessages element = SiriTopicMessages.forSituations(mapped(delivery), SX_CODESPACE, BREADCRUMB, STORED_AT).get(0);

        assertNotNull(element.xml());
        assertNotNull(element.avro(), () -> "Avro failed: " + element.avroError());
        assertEquals("", element.avro().kafkaHeaders().get(KafkaConstants.KEY));
    }

    @Test
    void emptyDeliveryGivesNoMessages() {
        Siri empty = TopicReferenceFixtures.etDelivery(List.of());

        assertTrue(SiriTopicMessages.forEstimatedTimetables(empty, ET_CODESPACE, BREADCRUMB, STORED_AT).isEmpty());
        assertTrue(SiriTopicMessages.forSituations(empty, SX_CODESPACE, BREADCRUMB, STORED_AT).isEmpty());
    }
}
