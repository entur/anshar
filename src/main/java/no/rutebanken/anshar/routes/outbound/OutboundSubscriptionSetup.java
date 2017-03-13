package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OutboundSubscriptionSetup implements Serializable {

    private ZonedDateTime requestTimestamp;
    private SubscriptionSetup.SubscriptionType subscriptionType;
    private SubscriptionSetup.SubscriptionMode subscriptionMode;
    private String address;
    private long heartbeatInterval;
    private int timeToLive;
    private SubscriptionSetup.ServiceType serviceType;
    private Map<Class, Set<String>> filterMap;
    private List<ValueAdapter> valueAdapters;
    private String subscriptionId;
    private String requestorRef;
    private ZonedDateTime initialTerminationTime;
    private boolean active;
    private String datasetId;
    private long changeBeforeUpdates;

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SubscriptionSetup.SubscriptionType subscriptionType, SubscriptionSetup.SubscriptionMode subscriptionMode, String address, long heartbeatInterval,
                                     long changeBeforeUpdates, SubscriptionSetup.ServiceType serviceType, Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, boolean active) {
        this.requestTimestamp = requestTimestamp;
        this.subscriptionType = subscriptionType;
        this.subscriptionMode = subscriptionMode;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.changeBeforeUpdates = changeBeforeUpdates;
        this.serviceType = serviceType;
        this.filterMap = filterMap;
        this.valueAdapters = valueAdapters;
        this.subscriptionId = subscriptionId;
        this.requestorRef = requestorRef;
        this.initialTerminationTime = initialTerminationTime;
        this.datasetId = datasetId;
        this.active = active;
    }

    OutboundSubscriptionSetup(SubscriptionSetup.SubscriptionType subscriptionType, String address, int timeToLive, List<ValueAdapter> outboundAdapters) {
        this.subscriptionType = subscriptionType;
        this.address = address;
        this.timeToLive = timeToLive;
        this.valueAdapters = outboundAdapters;
    }

    public ZonedDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public SubscriptionSetup.SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public SubscriptionSetup.SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
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

    public SubscriptionSetup.ServiceType getServiceType() {
        return serviceType;
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

    public boolean isActive() {
        return active;
    }

    public List<ValueAdapter> getValueAdapters() {
        return valueAdapters;
    }
}
