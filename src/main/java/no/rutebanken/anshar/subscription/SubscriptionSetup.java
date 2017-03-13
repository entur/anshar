package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;

public class SubscriptionSetup implements Serializable {

    private Logger logger = LoggerFactory.getLogger(SubscriptionSetup.class);
    private long internalId;
    private List<ValueAdapter> mappingAdapters = new ArrayList<>();
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
    private Map<Class, Set<Object>> filterMap;
    private List<String> idMappingPrefixes;
    private SubscriptionPreset[] mappingAdapterPresets;
    private SubscriptionPreset[] filterMapPresets;

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
    public SubscriptionSetup(SubscriptionType subscriptionType, SubscriptionMode subscriptionMode, String address, Duration heartbeatInterval, String operatorNamespace, Map<RequestType, String> urlMap,
                             String version, String vendor, String datasetId, ServiceType serviceType, List<ValueAdapter> mappingAdapters, Map<Class, Set<Object>> filterMap, List<String> idMappingPrefixes,
                             String subscriptionId, String requestorRef, Duration durationOfSubscription, boolean active) {
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
        this.idMappingPrefixes = idMappingPrefixes;
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

    public String getStartSubscriptionRouteName() {
        return getRouteName("start");
    }
    public String getCancelSubscriptionRouteName() {
        return getRouteName("cancel");
    }
    public String getCheckStatusRouteName() {
        return getRouteName("checkstatus");
    }
    public String getRequestResponseRouteName() {
        return getRouteName("request_response");
    }
    public String getServiceRequestRouteName() {
        return getRouteName("execute_request_response");
    }

    private String getRouteName(String prefix) {
        return prefix + subscriptionId;
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
        return MessageFormat.format("[vendor={0}, subscriptionId={1}, internalId={2}]", vendor, subscriptionId, internalId);
    }
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("internalId", getInternalId());
        obj.put("vendor", getVendor());
        obj.put("datasetId", getDatasetId());
        obj.put("subscriptionId", getSubscriptionId());
        obj.put("serviceType", getServiceType().toString());
        obj.put("subscriptionType", getSubscriptionType().toString());
        obj.put("subscriptionMode", getSubscriptionMode().toString());
        obj.put("heartbeatInterval", getHeartbeatInterval().toString());
        obj.put("durationOfSubscription", getDurationOfSubscription().toString());
        obj.put("requestorRef", getRequestorRef());

        return obj;
    }

    public long getInternalId() {
        return internalId;
    }

    public void setInternalId(long internalId) {
        this.internalId = internalId;
    }

    public void setSubscriptionMode(SubscriptionMode subscriptionMode) {
        this.subscriptionMode = subscriptionMode;
    }

    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public void setFilterPresets(SubscriptionPreset[] presets) {
        this.filterMapPresets = presets;
        filterMap = new HashMap<>();
        for (SubscriptionPreset preset : presets) {
            addFilterMap(new FilterMapPresets().get(preset));
        }
    }
    public void setFilterMap(Map<Class, Set<Object>> filterMap) {
        this.filterMap = filterMap;
    }

    public Map<Class, Set<Object>> getFilterMap() {
        return filterMap;
    }

    private void addFilterMap(Map<Class, Set<Object>> filters) {
        if (this.filterMap == null) {
            this.filterMap = new HashMap<>();
        }
        this.filterMap.putAll(filters);
    }

    public enum ServiceType {SOAP, REST}
    public enum SubscriptionType {SITUATION_EXCHANGE, VEHICLE_MONITORING, PRODUCTION_TIMETABLE, ESTIMATED_TIMETABLE}
    public enum SubscriptionMode {SUBSCRIBE, REQUEST_RESPONSE, FETCHED_DELIVERY}

    public void setMappingAdapterPresets(SubscriptionPreset[] mappingAdapterPresets) {
        this.mappingAdapterPresets = mappingAdapterPresets;
        mappingAdapters = new ArrayList<>();
        for (SubscriptionPreset preset : mappingAdapterPresets) {
            addMappingAdapters(new MappingAdapterPresets().get(preset));
        }
    }

    private void addMappingAdapters(List<ValueAdapter> valueAdapters) {
        if (mappingAdapters == null) {
            mappingAdapters = new ArrayList<>();
        }
        mappingAdapters.addAll(valueAdapters);
    }

    private void setMappingAdapters(List<ValueAdapter> mappingAdapters) {
        this.mappingAdapters = mappingAdapters;
    }


    public void setIdMappingPrefixes(List<String> idMappingPrefixes) {
        this.idMappingPrefixes = idMappingPrefixes;
    }

    public List<String> getIdMappingPrefixes() {
        return idMappingPrefixes;
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

    /**
     * Variant of equals that only compares fields crucial to detect updated subscription-config
     * NOTE: e.g. subscriptionId is NOT compared
     *
     * @param o
     * @return true if crucial config-elements are equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionSetup)) {
            return false;
        }

        SubscriptionSetup that = (SubscriptionSetup) o;

        if (getInternalId() != that.getInternalId()) {
            logger.info("getInternalId() does not match [{}] vs [{}]", getInternalId(), that.getInternalId());
            return false;
        }
        if (getSubscriptionType() != that.getSubscriptionType()) {
            logger.info("getSubscriptionType() does not match [{}] vs [{}]", getSubscriptionType(), that.getSubscriptionType());
            return false;
        }
        if (!address.equals(that.address)) {
            logger.info("address does not match [{}] vs [{}]", address, that.address);
            return false;
        }
        if (!getHeartbeatInterval().equals(that.getHeartbeatInterval())) {
            logger.info("getHeartbeatInterval() does not match [{}] vs [{}]", getHeartbeatInterval(), that.getHeartbeatInterval());
            return false;
        }
        if (getOperatorNamespace() != null ? !getOperatorNamespace().equals(that.getOperatorNamespace()) : that.getOperatorNamespace() != null) {
            logger.info("getOperatorNamespace() does not match [{}] vs [{}]", getOperatorNamespace(), that.getOperatorNamespace());
            return false;
        }
        if (!getUrlMap().equals(that.getUrlMap())) {
            logger.info("getUrlMap() does not match [{}] vs [{}]", getUrlMap(), that.getUrlMap());
            return false;
        }
        if (!getVersion().equals(that.getVersion())) {
            logger.info("getVersion() does not match [{}] vs [{}]", getVersion(), that.getVersion());
            return false;
        }
        if (!getVendor().equals(that.getVendor())) {
            logger.info("getVendor() does not match [{}] vs [{}]", getVendor(), that.getVendor());
            return false;
        }
        if (!getDatasetId().equals(that.getDatasetId())) {
            logger.info("getDatasetId() does not match [{}] vs [{}]", getDatasetId(), that.getDatasetId());
            return false;
        }
        if (getServiceType() != that.getServiceType()) {
            logger.info("getServiceType() does not match [{}] vs [{}]", getServiceType(), that.getServiceType());
            return false;
        }
        if (getDurationOfSubscription() != null ? !getDurationOfSubscription().equals(that.getDurationOfSubscription()) : that.getDurationOfSubscription() != null) {
            logger.info("getDurationOfSubscription() does not match [{}] vs [{}]", getDurationOfSubscription(), that.getDurationOfSubscription());
            return false;
        }
        if (getRequestorRef() != null ? !getRequestorRef().equals(that.getRequestorRef()) : that.getRequestorRef() != null) {
            logger.info("getRequestorRef() does not match [{}] vs [{}]", getRequestorRef(), that.getRequestorRef());
            return false;
        }
        if (getSubscriptionMode() != that.getSubscriptionMode()) {
            logger.info("getSubscriptionMode() does not match [{}] vs [{}]", getSubscriptionMode(), that.getSubscriptionMode());
            return false;
        }
        if (getIdMappingPrefixes() != null ? !getIdMappingPrefixes().equals(that.getIdMappingPrefixes()) : that.getIdMappingPrefixes() != null) {
            logger.info("getIdMappingPrefixes() does not match [{}] vs [{}]", getIdMappingPrefixes(), that.getIdMappingPrefixes());
            return false;
        }
        if (!Arrays.equals(mappingAdapterPresets, that.mappingAdapterPresets)) {
            logger.info("mappingAdapterPresets does not match [{}] vs [{}]", mappingAdapterPresets, that.mappingAdapterPresets);
            return false;
        }
        if (!Arrays.equals(filterMapPresets, that.filterMapPresets)) {
            logger.info("filterMapPresets does not match [{}] vs [{}]", filterMapPresets, that.filterMapPresets);
            return false;
        }
        return true;
    }
}
