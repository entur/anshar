package no.rutebanken.anshar.routes.kafka;

import net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;

public abstract class KafkaConfig extends RouteBuilder {


    private final static String jaasConfigContents = "org.apache.kafka.common.security.scram.ScramLoginModule required\nusername=\"%s\"\npassword=\"%s\";";
    @Value("${anshar.kafka.enabled:false}")
    protected boolean kafkaEnabled;

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

    protected String getSaslJaasConfigString() {
        if (StringUtils.isEmpty(saslUsername) || StringUtils.isEmpty(saslPassword)) {
            return null;
        }
        return String.format(jaasConfigContents, saslUsername.trim(), saslPassword.trim());
    }
}
