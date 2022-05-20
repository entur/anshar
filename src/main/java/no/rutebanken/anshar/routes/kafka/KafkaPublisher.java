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
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

@Service
public class KafkaPublisher extends KafkaConfig {
    private final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);

    public static final String CODESPACE_ID_KAFKA_HEADER_NAME = "codespaceId";

    private KafkaProducer producer;

    @Autowired
    PrometheusMetricsService metricsService;

    @PostConstruct
    public void init() {
        if (!kafkaEnabled & !kafkaEnrichEtEnabled) {
            return;
        }

        // Using default configuration as suggested by Camel
        KafkaConfiguration config = new KafkaConfiguration();

        Properties properties = config.createProducerProperties();

        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        properties.put(ProducerConfig.RETRIES_CONFIG, "10");
        properties.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "100");

        // Security
        if (!StringUtils.isEmpty(saslUsername) && !StringUtils.isEmpty(saslPassword)) {
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

            properties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
            properties.put(
                SaslConfigs.SASL_JAAS_CONFIG,
                    getSaslJaasConfigString()
            );
        } else {
            log.info("Skip Kafka-authentication as no user/passowrd is set");
        }
        producer = new KafkaProducer(properties);

    }

    public void publishToKafka(String topicName, String siriData, Map<String, String> metadataHeaders) {

        if (producer != null) {

            final ProducerRecord record = new ProducerRecord(topicName, siriData);
            addHeaderIfExists(metadataHeaders, record, CODESPACE_ID_KAFKA_HEADER_NAME);
            addHeaderIfExists(metadataHeaders, record, "subscriptionId");
            addHeaderIfExists(metadataHeaders, record, "target_topic");
            addHeaderIfExists(metadataHeaders, record, "breadcrumbId");

            //Fire and forget
            producer.send(record, createCallback(topicName));
            metricsService.registerKafkaRecord(topicName, PrometheusMetricsService.KafkaStatus.SENT);
        }

    }

    private void addHeaderIfExists(Map<String, String> metadataHeaders, ProducerRecord record, String headerName) {
        if (metadataHeaders.containsKey(headerName)) {
            record.headers().add(headerName,
                    metadataHeaders.get(headerName)
                            .getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private Callback createCallback(final String topicName) {
        return (recordMetadata, e) -> {
            if (e != null) {
                // Failed
                log.warn("Publishing to kafka failed", e);
                metricsService.registerKafkaRecord(topicName, PrometheusMetricsService.KafkaStatus.FAILED);
            } else {
                // Success
                log.info("Published to kafka topic {}", topicName);
                metricsService.registerKafkaRecord(topicName, PrometheusMetricsService.KafkaStatus.ACKED);
            }
        };
    }

    @Override
    public void configure() throws Exception {
        // @WIP: Ignore for now
    }
}
