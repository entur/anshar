/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *  https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.anshar.routes.kafka;

import net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

@Service
public class KafkaEnrichmentConsumer extends KafkaConfig {
    private final Logger log = LoggerFactory.getLogger(KafkaEnrichmentConsumer.class);

    @Autowired
    PrometheusMetricsService metricsService;


    @Value("${anshar.kafka.siri.enrich.et.processed.name:}")
    private String kafkaEnrichEtProcessedTopic;

    @Value("${anshar.kafka.siri.enrich.et.enabled}")
    private boolean kafkaEnrichEtEnabled;

    @Produce("direct:enqueue.message")
    protected ProducerTemplate siriEnqueueProducer;

    @PostConstruct
    public void init() {

        if (!kafkaEnrichEtEnabled) {
            return;
        }
        // Using default configuration as suggested by Camel
//        KafkaConfiguration config = new KafkaConfiguration();

        Properties properties = new Properties();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, clientId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                LongDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Security
        if (!StringUtils.isEmpty(saslUsername) && !StringUtils.isEmpty(saslPassword)) {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

            properties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
            properties.put(
                SaslConfigs.SASL_JAAS_CONFIG,
                    getSaslJaasConfigString()
            );
        } else {
            log.info("Skip Kafka-authentication as no user/password is set");
        }

        Consumer<Long, String> consumer = new KafkaConsumer(properties);
        consumer.subscribe(Collections.singletonList(kafkaEnrichEtProcessedTopic));

        while (true) {
            final ConsumerRecords<Long, String> consumerRecords = consumer.poll(Duration.ofMillis(100));

            consumerRecords.forEach(record -> {
                siriEnqueueProducer.sendBody(record.value());
            });

            consumer.commitAsync();
        }
    }
}
