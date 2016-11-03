package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;

public class SubscriptionSetup implements Serializable{

    private List<ValueAdapter> mappingAdapters;
    private SubscriptionType subscriptionType;
    private String address;
    private Duration heartbeatInterval;
    private String operatorNamespace;
    private Map<RequestType, String> urlMap;
    private String subscriptionId;
    private String version;
    private String vendor;
    private String datasetId;
    private ServiceType serviceType;
    private Duration durationOfSubscription;
    private String requestorRef;
    private boolean active;
    private SubscriptionMode subscriptionMode;
    private Map<Class, Set<String>> filterMap;

    public SubscriptionSetup() {
    }

    /**
     * @param subscriptionType SX, VM, ET
     * @param address Base-URL for receiving incoming data
     * @param heartbeatInterval Requested heartbeatinterval for subscriptions, Request-interval for Request/Response "subscriptions"
     * @param operatorNamespace Namespace
     * @param urlMap Operation-names and corresponding URL's
     * @param version SIRI-version to use
     * @param vendor Vendorname - information only
     * @param serviceType SOAP/REST
     * @param mappingAdapters
     * @param filterMap
     * @param subscriptionId Sets the subscriptionId to use
     * @param requestorRef
     * @param durationOfSubscription Initial duration of subscription
     * @param active Activates/deactivates subscription
     */
    SubscriptionSetup(SubscriptionType subscriptionType, SubscriptionMode subscriptionMode, String address, Duration heartbeatInterval, String operatorNamespace, Map<RequestType, String> urlMap,
                             String version, String vendor, String datasetId, ServiceType serviceType, List<ValueAdapter> mappingAdapters, Map<Class, Set<String>> filterMap, String subscriptionId,
                             String requestorRef, Duration durationOfSubscription, boolean active) {
        this.subscriptionType = subscriptionType;
        this.subscriptionMode = subscriptionMode;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.operatorNamespace = operatorNamespace;
        this.urlMap = urlMap;
        this.version = version;
        this.vendor = vendor;
        this.datasetId = datasetId;
        this.serviceType = serviceType;
        this.mappingAdapters = mappingAdapters;
        this.filterMap = filterMap;
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

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public String getOperatorNamespace() {
        return operatorNamespace;
    }

    public Map<RequestType, String> getUrlMap() {
        return urlMap;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getVersion() {
        return version;
    }

    public String getVendor() {
        return vendor;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public List<ValueAdapter> getMappingAdapters() {
        return mappingAdapters;
    }

    public Duration getDurationOfSubscription() {
        return durationOfSubscription;
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
        obj.put("subscriptionMode", getSubscriptionMode().toString());
        obj.put("heartbeatInterval", getHeartbeatInterval().toString());
        obj.put("durationOfSubscription", getDurationOfSubscription().toString());

        return obj;
    }

    public void setSubscriptionMode(SubscriptionMode subscriptionMode) {
        this.subscriptionMode = subscriptionMode;
    }

    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public void setFilterMap(Map<Class, Set<String>> filterMap) {
        this.filterMap = filterMap;
    }

    public Map<Class, Set<String>> getFilterMap() {
        return filterMap;
    }

    public enum ServiceType {SOAP, REST}
    public enum SubscriptionType {SITUATION_EXCHANGE, VEHICLE_MONITORING, PRODUCTION_TIMETABLE, ESTIMATED_TIMETABLE}
    public enum SubscriptionMode {SUBSCRIBE, REQUEST_RESPONSE}

    public void setMappingAdapterPreset(MappingAdapterPresets.Preset preset) {
        setMappingAdapters(MappingAdapterPresets.adapterPresets.get(preset));
    }

    private void setMappingAdapters(List<ValueAdapter> mappingAdapters) {
        this.mappingAdapters = mappingAdapters;
    }

    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setHeartbeatIntervalSeconds(int seconds) {
        setHeartbeatInterval(Duration.ofSeconds(seconds));
    }

    private void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public void setOperatorNamespace(String operatorNamespace) {
        this.operatorNamespace = operatorNamespace;
    }

    public void setUrlMap(Map<RequestType, String> urlMap) {
        this.urlMap = urlMap;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public void setDurationOfSubscriptionHours(int hours) {
        this.durationOfSubscription = Duration.ofHours(hours);
    }

    public void setRequestorRef(String requestorRef) {
        this.requestorRef = requestorRef;
    }
}
