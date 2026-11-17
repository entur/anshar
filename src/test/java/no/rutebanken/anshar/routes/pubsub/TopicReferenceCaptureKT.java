package no.rutebanken.anshar.routes.pubsub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.avro.AvroConvertorProcessor;
import no.rutebanken.anshar.routes.siri.transformer.SiriOutputTransformerRoute;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.Siri;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures.ET_CODESPACE;
import static no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures.STORED_AT;
import static no.rutebanken.anshar.routes.pubsub.TopicReferenceFixtures.SX_CODESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Records what the Camel outbound topic routes publish today for {@link TopicReferenceFixtures}, as reference
 * for the direct topic publisher. Needs the Pub/Sub emulator, so it is not part of {@code mvn test}:
 * <pre>
 * gcloud beta emulators pubsub start --project=test --host-port=localhost:8085
 * mvn test -Dtest=TopicReferenceCaptureKT -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * Writes src/test/resources/topic-reference/{et,sx}-{xml,avro}.json: one entry per message, ordered by delivery
 * and then by element id, with the message attributes and the payload as text. The breadcrumbId attribute, a new
 * Camel exchange id on every run, is written as {@value #BREADCRUMB_PLACEHOLDER}.
 */
class TopicReferenceCaptureKT {

    private static final String EMULATOR = "localhost:8085";
    private static final String PROJECT = "test";
    private static final Path OUTPUT = Path.of("src/test/resources/topic-reference");
    static final String BREADCRUMB_PLACEHOLDER = "<breadcrumbId>";

    // Matches the element id in XML (<DatedVehicleJourneyRef>id<) and in Avro JSON ("datedVehicleJourneyRef":{"string":"id")
    private static final Pattern ET_ID = Pattern.compile("(?i)datedVehicleJourneyRef\\W+(?:string\\W+)?([^<\"]+)");
    private static final Pattern SX_ID = Pattern.compile("(?i)situationNumber\\W+(?:string\\W+)?([^<\"]+)");

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void captureCurrentRoutes() throws Exception {
        for (String topic : List.of("ref.xml.et", "ref.avro.et", "ref.xml.sx", "ref.avro.sx")) {
            recreateTopicWithSubscription(topic);
        }

        PrometheusMetricsService metrics = new PrometheusMetricsService();
        AvroConvertorProcessor avroConvertor = new AvroConvertorProcessor();

        PubsubTopicRoute topicRoute = new PubsubTopicRoute();
        ReflectionTestUtils.setField(topicRoute, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(topicRoute, "etTopicXml", "google-pubsub:" + PROJECT + ":ref.xml.et");
        ReflectionTestUtils.setField(topicRoute, "sxTopicXml", "google-pubsub:" + PROJECT + ":ref.xml.sx");
        ReflectionTestUtils.setField(topicRoute, "fmTopicXml", "");
        ReflectionTestUtils.setField(topicRoute, "splitPublishThreads", 4);
        ReflectionTestUtils.setField(topicRoute, "avroConvertorProcessor", avroConvertor);
        ReflectionTestUtils.setField(topicRoute, "metricsService", metrics);

        PubsubAvroPublisherRoute avroRoute = new PubsubAvroPublisherRoute();
        ReflectionTestUtils.setField(avroRoute, "publishEtAvroToPubsubEnabled", true);
        ReflectionTestUtils.setField(avroRoute, "pubsubEtAvroJsonTopic", "google-pubsub:" + PROJECT + ":ref.avro.et");
        ReflectionTestUtils.setField(avroRoute, "publishSxAvroToPubsubEnabled", true);
        ReflectionTestUtils.setField(avroRoute, "pubsubSxAvroJsonTopic", "google-pubsub:" + PROJECT + ":ref.avro.sx");
        ReflectionTestUtils.setField(avroRoute, "publishVmAvroToPubsubEnabled", false);
        ReflectionTestUtils.setField(avroRoute, "pubsubVmAvroJsonTopic", "");
        ReflectionTestUtils.setField(avroRoute, "metricsService", metrics);
        ReflectionTestUtils.setField(avroRoute, "siriAvroJsonSerializer", new SiriAvroJsonSerializer());

        SiriOutputTransformerRoute transformerRoute = new SiriOutputTransformerRoute();
        ReflectionTestUtils.setField(transformerRoute, "mappingAdapterPresets", new MappingAdapterPresets(null));

        CamelContext context = new DefaultCamelContext();
        // As SubscriptionInitializer does, so exchanges carry a breadcrumbId header
        context.setUseBreadcrumb(true);
        GooglePubsubComponent pubsub = new GooglePubsubComponent();
        pubsub.setEndpoint(EMULATOR);
        pubsub.setAuthenticate(false);
        context.addComponent("google-pubsub", pubsub);
        context.addRoutes(transformerRoute);
        context.addRoutes(topicRoute);
        context.addRoutes(avroRoute);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publish.et.avro.kafka").log("Kafka stub");
                from("direct:publish.vm.avro.kafka").log("Kafka stub");
                from("direct:publish.sx.avro.kafka").log("Kafka stub");
            }
        });
        context.start();
        ProducerTemplate producer = context.createProducerTemplate();

        List<Siri> et = TopicReferenceFixtures.etDeliveries();
        List<Siri> sx = TopicReferenceFixtures.sxDeliveries();
        List<List<Message>> etXml = new ArrayList<>();
        List<List<Message>> etAvro = new ArrayList<>();
        List<List<Message>> sxXml = new ArrayList<>();
        List<List<Message>> sxAvro = new ArrayList<>();
        try {
            // One delivery at a time, so every pulled message belongs to the delivery just sent
            for (int i = 0; i < et.size(); i++) {
                int size = TopicReferenceFixtures.ET_DELIVERY_SIZES[i];
                producer.requestBodyAndHeaders("direct:send.to.pubsub.topic.estimated_timetable", et.get(i),
                        headers(ET_CODESPACE));
                etXml.add(pull("ref.xml.et", size));
                etAvro.add(pull("ref.avro.et", size));
            }
            for (int i = 0; i < sx.size(); i++) {
                int size = TopicReferenceFixtures.SX_DELIVERY_SIZES[i];
                producer.requestBodyAndHeaders("direct:send.to.pubsub.topic.situation_exchange", sx.get(i),
                        headers(SX_CODESPACE));
                sxXml.add(pull("ref.xml.sx", size));
                sxAvro.add(pull("ref.avro.sx", size));
            }
        } finally {
            context.stop();
        }

        Files.createDirectories(OUTPUT);
        write("et-xml.json", etXml, ET_ID);
        write("et-avro.json", etAvro, ET_ID);
        write("sx-xml.json", sxXml, SX_ID);
        write("sx-avro.json", sxAvro, SX_ID);
    }

    private static Map<String, Object> headers(String codespace) {
        return Map.of("codespaceId", codespace, PrometheusMetricsService.STORED_AT_HEADER, STORED_AT);
    }

    record Message(Map<String, String> attributes, String data, String orderingKey) {
    }

    private void write(String file, List<List<Message>> deliveries, Pattern idPattern) throws Exception {
        ArrayNode out = json.createArrayNode();
        for (int delivery = 0; delivery < deliveries.size(); delivery++) {
            List<Message> messages = new ArrayList<>(deliveries.get(delivery));
            messages.sort(Comparator.comparing(m -> elementId(m.data(), idPattern)));
            for (Message message : messages) {
                ObjectNode entry = out.addObject();
                entry.put("delivery", delivery);
                entry.set("attributes", json.valueToTree(message.attributes()));
                entry.put("orderingKey", message.orderingKey());
                entry.put("data", message.data());
            }
        }
        json.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.resolve(file).toFile(), out);
    }

    private static String elementId(String data, Pattern idPattern) {
        Matcher matcher = idPattern.matcher(data);
        while (matcher.find()) {
            if (!matcher.group(1).startsWith("null")) {
                return matcher.group(1);
            }
        }
        throw new IllegalStateException("No element id in message: " + data.substring(0, Math.min(200, data.length())));
    }

    private List<Message> pull(String topic, int expected) throws Exception {
        List<Message> messages = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 30_000;
        while (messages.size() < expected && System.currentTimeMillis() < deadline) {
            JsonNode response = json.readTree(send("POST", "/subscriptions/" + topic + ":pull",
                    "{\"maxMessages\":100,\"returnImmediately\":true}"));
            List<String> ackIds = new ArrayList<>();
            for (JsonNode received : response.path("receivedMessages")) {
                JsonNode message = received.get("message");
                Map<String, String> attributes = new java.util.TreeMap<>();
                message.path("attributes").fields().forEachRemaining(a -> attributes.put(a.getKey(), a.getValue().asText()));
                // A new Camel exchange id on every run; only its presence is part of the reference
                attributes.computeIfPresent("breadcrumbId", (key, value) -> BREADCRUMB_PLACEHOLDER);
                String data = new String(Base64.getDecoder().decode(message.path("data").asText()), StandardCharsets.UTF_8);
                messages.add(new Message(attributes, data, message.path("orderingKey").asText("")));
                ackIds.add(received.get("ackId").asText());
            }
            if (!ackIds.isEmpty()) {
                send("POST", "/subscriptions/" + topic + ":acknowledge", json.writeValueAsString(Map.of("ackIds", ackIds)));
            }
        }
        assertEquals(expected, messages.size(), "messages pulled from " + topic);
        return messages;
    }

    private void recreateTopicWithSubscription(String topic) throws Exception {
        send("DELETE", "/subscriptions/" + topic, null);
        send("DELETE", "/topics/" + topic, null);
        send("PUT", "/topics/" + topic, "{}");
        send("PUT", "/subscriptions/" + topic,
                "{\"topic\":\"projects/" + PROJECT + "/topics/" + topic + "\",\"ackDeadlineSeconds\":60}");
    }

    private String send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + EMULATOR + "/v1/projects/" + PROJECT + path))
                .header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
    }
}
