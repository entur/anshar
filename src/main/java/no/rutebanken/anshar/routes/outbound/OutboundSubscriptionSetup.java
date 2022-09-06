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

package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri.validator.SiriValidator;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OutboundSubscriptionSetup implements Serializable {

    private SiriValidator.Version siriVersion = SiriValidator.Version.VERSION_2_0;
    private ZonedDateTime requestTimestamp;
    private final SiriDataType subscriptionType;
    private final String address;
    private long heartbeatInterval;
    private int timeToLive;
    private Map<Class, Set<String>> filterMap;
    private final List<ValueAdapter> valueAdapters;
    private final String subscriptionId;
    private String requestorRef;
    private ZonedDateTime initialTerminationTime;
    private String datasetId;
    private String clientTrackingName;
    private long changeBeforeUpdates;

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SiriDataType subscriptionType, String address, long heartbeatInterval,
                                     long changeBeforeUpdates, Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, String clientTrackingName,
                                     SiriValidator.Version siriVersion) {
        this.requestTimestamp = requestTimestamp;
        this.subscriptionType = subscriptionType;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.changeBeforeUpdates = changeBeforeUpdates;
        this.filterMap = filterMap;
        this.valueAdapters = valueAdapters;
        this.subscriptionId = subscriptionId;
        this.requestorRef = requestorRef;
        this.initialTerminationTime = initialTerminationTime;
        this.datasetId = datasetId;
        this.clientTrackingName = clientTrackingName;
        this.siriVersion = siriVersion;
    }

    OutboundSubscriptionSetup(SiriDataType subscriptionType, String address, int timeToLive, List<ValueAdapter> outboundAdapters, String subscriptionId) {
        this.subscriptionType = subscriptionType;
        this.address = address;
        this.timeToLive = timeToLive;
        this.valueAdapters = outboundAdapters;
        this.subscriptionId = subscriptionId;
    }

    public String createRouteId() {
        return "outbound." + subscriptionType + "." + subscriptionId;
    }

    public ZonedDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public SiriDataType getSubscriptionType() {
        return subscriptionType;
    }

    public String getAddress() {
        return address;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    int getTimeToLive() {
        return timeToLive;
    }

    public long getChangeBeforeUpdates() {
        return changeBeforeUpdates;
    }

    public Map<Class, Set<String>> getFilterMap() {
        return filterMap;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getRequestorRef() {
        return requestorRef;
    }

    public ZonedDateTime getInitialTerminationTime() {
        return initialTerminationTime;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public List<ValueAdapter> getValueAdapters() {
        return valueAdapters;
    }

    public String getClientTrackingName() {
        return clientTrackingName;
    }

    public SiriValidator.Version getSiriVersion() {
        return siriVersion;
    }

    public String toString() {
        return MessageFormat.format("[subscriptionId={0}, clientTrackingName={1}, requestorRef={2}, address={3}]", subscriptionId, clientTrackingName, requestorRef, address);
    }
}
