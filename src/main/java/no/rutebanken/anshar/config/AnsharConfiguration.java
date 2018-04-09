package no.rutebanken.anshar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnsharConfiguration {

    @Value("${rutebanken.kubernetes.url:}")
    private String kubernetesUrl;

    @Value("${rutebanken.kubernetes.enabled:true}")
    private boolean kubernetesEnabled;

    @Value("${rutebanken.kubernetes.namespace:default}")
    private String namespace;

    @Value("${rutebanken.hazelcast.management.url:}")
    private String hazelcastManagementUrl;

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.incoming.activemq.concurrentConsumers}")
    private long concurrentConsumers;

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    @Value("${anshar.incoming.activemq.timetolive}")
    private String timeToLive;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.healthcheck.interval.seconds}")
    private int healthCheckInterval = 30;

    @Value("${anshar.environment}")
    private String environment;

    @Value("${anshar.default.max.elements.per.delivery:1500}")
    private int defaultMaxSize;

    @Value("${anshar.outbound.polling.tracking.period.minutes:30}")
    private int trackingPeriodMinutes;

    @Value("${anshar.outbound.adhoc.tracking.period.minutes:3}")
    private int adHocTrackingPeriodMinutes;

    @Value("${anshar.siri.default.producerRef:ENT}")
    private String producerRef;

    @Value("${anshar.siri.sx.graceperiod.minutes:0}")
    private long sxGraceperiodMinutes;

    @Value("${anshar.siri.et.graceperiod.minutes:0}")
    private long etGraceperiodMinutes;

    @Value("${anshar.siri.vm.graceperiod.minutes:0}")
    private long vmGraceperiodMinutes;

    @Value("${anshar.siri.pt.graceperiod.minutes:0}")
    private long ptGraceperiodMinutes;

    public String getHazelcastManagementUrl() {
        return hazelcastManagementUrl;
    }

    public String getKubernetesUrl() {
        return kubernetesUrl;
    }

    public boolean isKubernetesEnabled() {
        return kubernetesEnabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getInboundPort() {
        return inboundPort;
    }

    public String getIncomingPathPattern() {
        return incomingPathPattern;
    }

    public String getTimeToLive() {
        return timeToLive;
    }

    public long getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getInboundUrl() {
        return inboundUrl;
    }

    public String getIncomingLogDirectory() {
        return incomingLogDirectory;
    }

    public int getDefaultMaxSize() {
        return defaultMaxSize;
    }

    public int getTrackingPeriodMinutes() {
        return trackingPeriodMinutes;
    }

    public int getAdHocTrackingPeriodMinutes() {
        return adHocTrackingPeriodMinutes;
    }

    public String getProducerRef() {
        return producerRef;
    }

    public long getSxGraceperiodMinutes() {
        return sxGraceperiodMinutes;
    }

    public long getEtGraceperiodMinutes() {
        return etGraceperiodMinutes;
    }

    public long getVmGraceperiodMinutes() {
        return vmGraceperiodMinutes;
    }

    public long getPtGraceperiodMinutes() {
        return ptGraceperiodMinutes;
    }
}
