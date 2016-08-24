package no.rutebanken.anshar.subscription;

import org.json.simple.JSONObject;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Map;

public class SubscriptionSetup implements Serializable{
    private SubscriptionType subscriptionType;
    private String address;
    private Duration heartbeatInterval;
    private String operatorNamespace;
    private Map<String, String> urlMap;
    private String subscriptionId;
    private String version;
    private String vendor;
    private String datasetId;
    private ServiceType serviceType;
    private Duration durationOfSubscription;
    private String requestorRef;
    private boolean active;

    /**
     * @param subscriptionType SX, VM, ET
     * @param address Base-URL for receiving incoming data
     * @param heartbeatInterval Requested heartbeatinterval for subscriptions, Request-interval for Request/Response "subscriptions"
     * @param operatorNamespace Namespace
     * @param urlMap Operation-names and corresponding URL's
     * @param version SIRI-version to use
     * @param vendor Vendorname - information only
     * @param serviceType SOAP/REST
     * @param subscriptionId Sets the subscriptionId to use
     * @param requestorRef
     * @param durationOfSubscription Initial duration of subscription
     * @param active Activates/deactivates subscription
     */
    public SubscriptionSetup(SubscriptionType subscriptionType, String address, Duration heartbeatInterval, String operatorNamespace, Map<String, String> urlMap,
                             String version, String vendor, String datasetId, ServiceType serviceType, String subscriptionId,
                             String requestorRef, Duration durationOfSubscription, boolean active) {
        this.subscriptionType = subscriptionType;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.operatorNamespace = operatorNamespace;
        this.urlMap = urlMap;
        this.version = version;
        this.vendor = vendor;
        this.datasetId = datasetId;
        this.serviceType = serviceType;
        this.subscriptionId = subscriptionId;
        this.requestorRef = requestorRef;
        this.durationOfSubscription = durationOfSubscription;
        this.active = active;
    }

    public String buildUrl() {
        return buildUrl(true);
    }

    public String buildUrl(boolean includeServerAddress) {
        return (includeServerAddress ? address:"") + MessageFormat.format("/{0}/{1}/{2}/{3}", version, serviceType == ServiceType.REST ? "rs" : "ws", vendor, subscriptionId);
    }

    private void setAddress(String address) {
        this.address = address;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    private void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }


    private void setOperatorNamespace(String operatorNamespace) {
        this.operatorNamespace = operatorNamespace;
    }

    public String getOperatorNamespace() {
        return operatorNamespace;
    }

    private void setUrlMap(Map<String, String> urlMap) {
        this.urlMap = urlMap;
    }

    public Map<String, String> getUrlMap() {
        return urlMap;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getVersion() {
        return version;
    }

    private void setVersion(String version) {
        this.version = version;
    }

    public String getVendor() {
        return vendor;
    }

    private void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    private void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Duration getDurationOfSubscription() {
        return durationOfSubscription;
    }

    private void setDurationOfSubscription(Duration durationOfSubscription) {
        this.durationOfSubscription = durationOfSubscription;
    }

    private void setRequestorRef(String requestorRef) {
        this.requestorRef = requestorRef;
    }

    public String getRequestorRef() {
        return requestorRef;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String toString() {
        return MessageFormat.format("[vendor={0}, id={1}]", vendor, subscriptionId);
    }
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("vendor", getVendor());
        obj.put("datasetId", getDatasetId());
        obj.put("subscriptionId", getSubscriptionId());
        obj.put("serviceType", getServiceType().toString());
        obj.put("subscriptionType", getSubscriptionType().toString());
        obj.put("heartbeatInterval", getHeartbeatInterval().toString());
        obj.put("durationOfSubscription", getDurationOfSubscription().toString());

        return obj;
    }

    public enum ServiceType {SOAP, REST}
    public enum SubscriptionType {SITUATION_EXCHANGE, VEHICLE_MONITORING, PRODUCTION_TIMETABLE, ESTIMATED_TIMETABLE}
}
