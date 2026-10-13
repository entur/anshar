package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.KafkaComponent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MetricsKafkaClientFactoryWiringTest extends SpringBootBaseTest {

    @Autowired
    private CamelContext camelContext;

    @Test
    public void testKafkaComponentUsesMetricsClientFactory() {
        KafkaComponent kafkaComponent = camelContext.getComponent("kafka", KafkaComponent.class);

        assertInstanceOf(MetricsKafkaClientFactory.class, kafkaComponent.getKafkaClientFactory());
    }
}
