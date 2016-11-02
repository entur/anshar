package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

public class OutboundSubscriptionSetup {

    private final ZonedDateTime requestTimestamp;
    private final SubscriptionSetup.SubscriptionType subscriptionType;
    private final SubscriptionSetup.SubscriptionMode subscriptionMode;
    private final String address;
    private final long heartbeatInterval;
    private final SubscriptionSetup.ServiceType serviceType;
    private final Map<Class, Set<String>> filterMap;
    private final String subscriptionId;
    private final String requestorRef;
    private final ZonedDateTime initialTerminationTime;
    private final boolean active;
    private final String datasetId;

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SubscriptionSetup.SubscriptionType subscriptionType, SubscriptionSetup.SubscriptionMode subscriptionMode, String address, long heartbeatInterval, SubscriptionSetup.ServiceType serviceType,
                                     Map<Class, Set<String>> filterMap, String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, boolean active) {
        this.requestTimestamp = requestTimestamp;
        this.subscriptionType = subscriptionType;
        this.subscriptionMode = subscriptionMode;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.serviceType = serviceType;
        this.filterMap = filterMap;
        this.subscriptionId = subscriptionId;
        this.requestorRef = requestorRef;
        this.initialTerminationTime = initialTerminationTime;
        this.datasetId = datasetId;
        this.active = active;
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
}
