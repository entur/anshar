package no.rutebanken.anshar.routes.pubsub;

import io.micrometer.core.instrument.Timer;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.avro.AvroConvertorProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each publish to a Pub/Sub topic is timed on its own, so the time Pub/Sub takes to answer can be
 * told apart from the time spent waiting before publishing. The topic stubs take a fixed time to
 * answer.
 */
class PubsubPublishDurationTest {

    private static final long PUBLISH_MILLIS = 50;

    private CamelContext context;
    private final PrometheusMetricsService metrics = new PrometheusMetricsService();
    private ProducerTemplate producer;

    @BeforeEach
    void setUp() throws Exception {
        PubsubTopicRoute topicRoute = new PubsubTopicRoute();
        ReflectionTestUtils.setField(topicRoute, "pushToTopicEnabled", true);
        ReflectionTestUtils.setField(topicRoute, "etTopicXml", "direct:topic.xml.et");
        ReflectionTestUtils.setField(topicRoute, "sxTopicXml", "direct:topic.xml.sx");
        ReflectionTestUtils.setField(topicRoute, "fmTopicXml", "");
        ReflectionTestUtils.setField(topicRoute, "splitPublishThreads", 2);
        ReflectionTestUtils.setField(topicRoute, "avroConvertorProcessor", Mockito.mock(AvroConvertorProcessor.class));
        ReflectionTestUtils.setField(topicRoute, "metricsService", metrics);

        PubsubAvroPublisherRoute avroRoute = new PubsubAvroPublisherRoute();
        ReflectionTestUtils.setField(avroRoute, "publishEtAvroToPubsubEnabled", true);
        ReflectionTestUtils.setField(avroRoute, "pubsubEtAvroJsonTopic", "direct:topic.avro.et");
        ReflectionTestUtils.setField(avroRoute, "publishVmAvroToPubsubEnabled", true);
        ReflectionTestUtils.setField(avroRoute, "pubsubVmAvroJsonTopic", "direct:topic.avro.vm");
        ReflectionTestUtils.setField(avroRoute, "publishSxAvroToPubsubEnabled", true);
        ReflectionTestUtils.setField(avroRoute, "pubsubSxAvroJsonTopic", "direct:topic.avro.sx");
        ReflectionTestUtils.setField(avroRoute, "metricsService", metrics);
        ReflectionTestUtils.setField(avroRoute, "siriAvroJsonSerializer", Mockito.mock(SiriAvroJsonSerializer.class));

        context = new DefaultCamelContext();
        context.addRoutes(topicRoute);
        context.addRoutes(avroRoute);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:siri.transform.data").log("transform stub");
                for (String topic : new String[]{"xml.et", "xml.sx", "avro.et", "avro.vm", "avro.sx"}) {
                    from("direct:topic." + topic).delay(PUBLISH_MILLIS).syncDelayed();
                }
                from("direct:publish.et.avro.kafka").log("stub");
                from("direct:publish.vm.avro.kafka").log("stub");
                from("direct:publish.sx.avro.kafka").log("stub");
            }
        });
        context.start();
        producer = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private void assertPublishTimed(String dataType, String format, int expectedCount) {
        Timer timer = metrics.find("app.anshar.outbound.publish").tags("dataType", dataType, "format", format).timer();
        assertNotNull(timer, dataType + " " + format + " publish not timed");
        assertEquals(expectedCount, timer.count(), dataType + " " + format + " count");
        double meanMillis = timer.mean(TimeUnit.MILLISECONDS);
        assertTrue(meanMillis >= PUBLISH_MILLIS && meanMillis < 5_000, dataType + " " + format + " was " + meanMillis + " ms");
    }

    @Test
    void etXmlPublishIsTimed() {
        producer.sendBody("direct:publish.et.xml", "<Siri/>");

        assertPublishTimed("ESTIMATED_TIMETABLE", "xml", 1);
    }

    @Test
    void sxXmlPublishIsTimed() {
        producer.sendBody("direct:publish.sx.xml", "<Siri/>");

        assertPublishTimed("SITUATION_EXCHANGE", "xml", 1);
    }

    @Test
    void avroPublishIsTimedForEachDataType() {
        producer.sendBody("direct:publish.et.avro.pubsub", "{}");
        producer.sendBody("direct:publish.vm.avro.pubsub", "{}");
        producer.sendBody("direct:publish.sx.avro.pubsub", "{}");

        assertPublishTimed("ESTIMATED_TIMETABLE", "avro", 1);
        assertPublishTimed("VEHICLE_MONITORING", "avro", 1);
        assertPublishTimed("SITUATION_EXCHANGE", "avro", 1);
    }
}
