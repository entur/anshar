/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.kafka;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.apache.camel.component.kafka.DefaultKafkaClientFactory;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Properties;

/**
 * Creates the Kafka-clients used by Camel, and exposes their native Kafka-metrics through Prometheus.
 */
@Component
public class MetricsKafkaClientFactory extends DefaultKafkaClientFactory {

    private final PrometheusMetricsService metricsService;

    public MetricsKafkaClientFactory(PrometheusMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Bean
    public ComponentCustomizer kafkaClientFactoryCustomizer() {
        return ComponentCustomizer.forType(KafkaComponent.class, kafkaComponent -> kafkaComponent.setKafkaClientFactory(this));
    }

    @Override
    public Producer getProducer(Properties kafkaProps) {
        Producer producer = super.getProducer(kafkaProps);
        KafkaClientMetrics binder = new KafkaClientMetrics(producer);
        binder.bindTo(metricsService);
        return closeBinderOnClose(Producer.class, producer, binder);
    }

    @Override
    public Consumer getConsumer(Properties kafkaProps) {
        Consumer consumer = super.getConsumer(kafkaProps);
        KafkaClientMetrics binder = new KafkaClientMetrics(consumer);
        binder.bindTo(metricsService);
        return closeBinderOnClose(Consumer.class, consumer, binder);
    }

    /**
     * Camel owns - and closes - the client, but has no hook for disposal.
     * Wrapping the client ensures that the metrics-binder is closed along with it.
     */
    private static <T> T closeBinderOnClose(Class<T> clientType, T client, KafkaClientMetrics binder) {
        return clientType.cast(Proxy.newProxyInstance(
                clientType.getClassLoader(),
                new Class<?>[]{clientType},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        binder.close();
                    }
                    try {
                        return method.invoke(client, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }));
    }
}
