package no.rutebanken.anshar.metrics.avro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrometheusAvroConverterTest {

    @Test
    public void testMetricWithLabels() {
        String record = PrometheusAvroConverter.convertMetrics(
                "app_anshar_camel_inflight{endpoint=\"direct://outer\",routeId=\"outer\",} 3.0", "host");

        assertNotNull(record);
        assertTrue(record.contains("\"name\": \"app_anshar_camel_inflight\""), record);
        assertTrue(record.contains("\"value\": \"outer\""), record);
        assertTrue(record.contains("\"value\": 3.0"), record);
    }

    @Test
    public void testMetricWithoutLabels() {
        String record = PrometheusAvroConverter.convertMetrics("app_anshar_camel_inflight_oldest_seconds 189.0", "host");

        assertNotNull(record);
        assertTrue(record.contains("\"name\": \"app_anshar_camel_inflight_oldest_seconds\""), record);
        assertTrue(record.contains("\"labels\": []"), record);
        assertTrue(record.contains("\"value\": 189.0"), record);
    }
}
