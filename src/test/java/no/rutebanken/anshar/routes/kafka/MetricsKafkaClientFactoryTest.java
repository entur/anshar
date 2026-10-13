package no.rutebanken.anshar.routes.kafka;

import io.micrometer.core.instrument.Meter;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricsKafkaClientFactoryTest {

    private PrometheusMetricsService metricsService;
    private MetricsKafkaClientFactory factory;

    @BeforeEach
    public void init() {
        metricsService = new PrometheusMetricsService();
        factory = new MetricsKafkaClientFactory(metricsService);
    }

    @AfterEach
    public void tearDown() {
        metricsService.close();
    }

    @Test
    public void testProducerMetricsAreBoundOnCreation() {
        Producer producer = factory.getProducer(producerProperties());
        try {
            assertFalse(kafkaMeters("kafka.producer.").isEmpty(), "Expected native kafka producer-metrics to be registered");
        } finally {
            producer.close();
        }
    }

    @Test
    public void testProducerMetricsAreRemovedOnClose() {
        Producer producer = factory.getProducer(producerProperties());
        producer.close();

        assertTrue(kafkaMeters("kafka.").isEmpty(), "Expected kafka-metrics to be removed when producer is closed");
    }

    @Test
    public void testConsumerMetricsAreBoundOnCreation() {
        Consumer consumer = factory.getConsumer(consumerProperties());
        try {
            assertFalse(kafkaMeters("kafka.consumer.").isEmpty(), "Expected native kafka consumer-metrics to be registered");
        } finally {
            consumer.close();
        }
    }

    @Test
    public void testConsumerMetricsAreRemovedOnClose() {
        Consumer consumer = factory.getConsumer(consumerProperties());
        consumer.close();

        assertTrue(kafkaMeters("kafka.").isEmpty(), "Expected kafka-metrics to be removed when consumer is closed");
    }

    private List<Meter> kafkaMeters(String prefix) {
        return metricsService.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith(prefix))
                .toList();
    }

    // Clients are created without connecting, so no broker is needed
    private Properties producerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:9092");
        properties.put("client.id", "anshar-unit-test");
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        return properties;
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:9092");
        properties.put("client.id", "anshar-unit-test");
        properties.put("group.id", "anshar-unit-test");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return properties;
    }
}
