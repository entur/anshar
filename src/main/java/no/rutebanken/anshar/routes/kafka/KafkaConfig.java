package no.rutebanken.anshar.routes.kafka;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;

public abstract class KafkaConfig extends RouteBuilder {


    public static final String CODESPACE_ID_KAFKA_HEADER_NAME = "codespaceId";

    private final static String jaasConfigContents = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";";

    @Value("${anshar.kafka.et.enabled:false}")
    protected boolean publishEtToKafkaEnabled;
    @Value("${anshar.kafka.vm.enabled:false}")
    protected boolean publishVmToKafkaEnabled;
    @Value("${anshar.kafka.sx.enabled:false}")
    protected boolean publishSxToKafkaEnabled;

    @Value("${anshar.kafka.siri.enrich.et.enabled:false}")
    protected boolean kafkaEnrichEtEnabled;

    @Value("${anshar.kafka.security.protocol}")
    protected String securityProtocol;
    @Value("${anshar.kafka.security.sasl.mechanism}")
    protected String saslMechanism;
    @Value("${anshar.kafka.sasl.username}")
    protected String saslUsername;
    @Value("${anshar.kafka.sasl.password}")
    protected String saslPassword;
    @Value("${anshar.kafka.brokers}")
    protected String brokers;
    @Value("${anshar.kafka.clientId:}")
    protected String clientId;
    @Value("${anshar.kafka.compressionType:gzip}")
    private String compressionType;

    protected String createConsumerConfig(String topicName) {
        String config = topicName;
        config += "?brokers=" + brokers;
        config += "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer";
        config += "&clientId=" + clientId;
        config += "&groupId=" + clientId;
        config += "&securityProtocol=" + securityProtocol;
        config += "&saslMechanism=" + saslMechanism;
        config += "&saslJaasConfig=" + getSaslJaasConfigString();
        return config;
    }

    protected String createProducerConfig(String topicName) {
        String config = topicName;
        config += "?brokers=" + brokers;
        config += "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer";
        config += "&compressionCodec=" + compressionType;
        config += "&clientId=" + clientId;
        config += "&securityProtocol=" + securityProtocol;
        config += "&saslMechanism=" + saslMechanism;
        config += "&saslJaasConfig=" + getSaslJaasConfigString();
        return config;
    }

    private String getSaslJaasConfigString() {
        if (StringUtils.isEmpty(saslUsername) || StringUtils.isEmpty(saslPassword)) {
            return null;
        }
        return String.format(jaasConfigContents, saslUsername.trim(), saslPassword.trim());
    }
}
