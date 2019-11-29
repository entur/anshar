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

    @Value("${anshar.validation.profile.enabled}")
    private boolean profileValidation;

    @Value("${anshar.validation.profile.name}")
    private String validationProfileName;

    @Value("${anshar.tracking.header.required.post:false}")
    private boolean trackingHeaderRequiredforPost;

    @Value("${anshar.tracking.header.required.get:false}")
    private boolean trackingHeaderRequiredForGet;

    @Value("${anshar.tracking.header.name:Client-Name}")
    private String trackingHeaderName;

    @Value("${anshar.validation.total.max.size.mb:4}")
    private int maxTotalXmlSizeOfValidation;

    @Value("${anshar.validation.total.max.count:10}")
    private int maxNumberOfValidations;

    @Value("${anshar.validation.data.persist.hours:6}")
    private int numberOfHoursToKeepValidation;


    @Value("${anshar.tracking.data.buffer.commit.frequency.seconds:2}")
    private int changeBufferCommitFrequency;

    @Value("${anshar.message.queue.camel.route.prefix}")
    private String messageQueueCamelRoutePrefix;

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

    public boolean isProfileValidation() {
        return profileValidation;
    }

    public String getValidationProfileName() {
        return validationProfileName;
    }

    public boolean isTrackingHeaderRequiredforPost() {
        return trackingHeaderRequiredforPost;
    }

    public boolean isTrackingHeaderRequiredForGet() {
        return trackingHeaderRequiredForGet;
    }

    public String getTrackingHeaderName() {
        return trackingHeaderName;
    }

    public int getMaxTotalXmlSizeOfValidation() {
        return maxTotalXmlSizeOfValidation;
    }

    public int getMaxNumberOfValidations() {
        return maxNumberOfValidations;
    }

    public int getNumberOfHoursToKeepValidation() {
        return numberOfHoursToKeepValidation;
    }

    public int getChangeBufferCommitFrequency() {
        return changeBufferCommitFrequency;
    }

    public String getMessageQueueCamelRoutePrefix() {
        return messageQueueCamelRoutePrefix;
    }
}
