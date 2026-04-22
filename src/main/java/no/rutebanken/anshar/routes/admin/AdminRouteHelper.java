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

package no.rutebanken.anshar.routes.admin;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Facilities;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.CamelContext;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.DefaultedTextStructure;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class AdminRouteHelper implements ApplicationListener<ContextClosedEvent> {
    private final Logger logger = LoggerFactory.getLogger(AdminRouteHelper.class);

    private static final List<String> PUBSUB_CONSUMER_ROUTE_IDS = List.of(
            "incoming.transform.vm",
            "incoming.transform.et",
            "incoming.transform.sx",
            "incoming.transform.fm"
    );

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private SubscriptionManager subscriptionManager;


    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private Facilities facilities;

    @Autowired
    private ExtendedHazelcastService hazelcastService;

    protected boolean shutdownTriggered;

    public void flushDataFromSubscription(String subscriptionId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);
        if (subscriptionSetup != null) {
            executor.execute(() -> flushData(subscriptionSetup.getDatasetId(), subscriptionSetup.getSubscriptionType().name()));
        }
    }

    protected void flushDataFromCodespace(String codespaceId, String dataType) {
        if (codespaceId != null && dataType != null) {
            logger.info("Flushing all data of type {} for {}", dataType, codespaceId);
            flushData(codespaceId, dataType);
            logger.info("Flushed all data of type {} for {}", dataType, codespaceId);
        }
    }

    public JSONObject getSituationMetadataAsJson(String codespaceId) {
        Collection<PtSituationElement> allSituations = situations.getAll(codespaceId);
        JSONArray situations = new JSONArray();
        for (PtSituationElement ptSituationElement : allSituations) {
            JSONObject situationObj = new JSONObject();
            situationObj.put("situationNumber", getSituationNumber(ptSituationElement));//.getSituationNumber() != null ? ptSituationElement.getSituationNumber().getValue():"");
            situationObj.put("creationTime", ptSituationElement.getCreationTime());
            situationObj.put("validity", getValidities(ptSituationElement));
            situationObj.put("progress", ptSituationElement.getProgress());
            situationObj.put("summaries", getSummaries(ptSituationElement));
            situationObj.put("descriptions", getDescriptions(ptSituationElement));
            situationObj.put("advices", getAdvice(ptSituationElement));
            situations.add(situationObj);
        }
        JSONObject all = new JSONObject();
        all.put("situations", situations);
        return all;
    }

    private Object getAdvice(PtSituationElement element) {
        JSONArray jsonArray = new JSONArray();
        List<DefaultedTextStructure> advices = element.getAdvices();
        for (DefaultedTextStructure text : advices) {
            JSONObject obj = getTextJson(text);
            jsonArray.add(obj);
        }
        return jsonArray;
    }

    private Object getDescriptions(PtSituationElement element) {
        JSONArray jsonArray = new JSONArray();
        List<DefaultedTextStructure> descriptions = element.getDescriptions();
        for (DefaultedTextStructure text : descriptions) {
            JSONObject obj = getTextJson(text);
            jsonArray.add(obj);
        }
        return jsonArray;
    }

    private JSONArray getSummaries(PtSituationElement element) {
        JSONArray jsonArray = new JSONArray();
        List<DefaultedTextStructure> summaries = element.getSummaries();
        for (DefaultedTextStructure text : summaries) {
            JSONObject obj = getTextJson(text);
            jsonArray.add(obj);
        }
        return jsonArray;
    }

    private JSONObject getTextJson(DefaultedTextStructure summary) {
        JSONObject obj = new JSONObject();
        obj.put("value", summary.getValue() != null ? summary.getValue():"");
        obj.put("lang", summary.getLang() != null ? summary.getLang():"");
        return obj;
    }

    private JSONArray getValidities(PtSituationElement element) {
        JSONArray jsonArray = new JSONArray();

        List<HalfOpenTimestampOutputRangeStructure> validityPeriods = element.getValidityPeriods();
        if (validityPeriods != null) {
            for (HalfOpenTimestampOutputRangeStructure validity : validityPeriods) {
                JSONObject json = new JSONObject();

                json.put("startTime", validity.getStartTime());
                json.put("endTime", validity.getEndTime() != null ?  validity.getEndTime():"");
                jsonArray.add(json);
            }
        }
        return jsonArray;
    }

    private String getSituationNumber(PtSituationElement element) {
        if (element.getSituationNumber() != null) {
            return element.getSituationNumber().getValue();
        }
        return null;
    }

    public void deleteSubscription(String subscriptionId) {
        subscriptionManager.removeSubscription(subscriptionId, true);
    }

    public void forceUnlock(String lockId) {
        final String lockMap = "ansharRouteLockMap";
        logger.warn("Force unlocking of key {} from map {}", lockId, lockMap);
        hazelcastService.getHazelcastInstance().getMap(lockMap).forceUnlock(lockId);
    }

    public boolean isNotShuttingDown() {
        return !shutdownTriggered;
    }

    /**
     * Fires early in Spring shutdown (before @PreDestroy and before Camel's SmartLifecycle.stop()),
     * giving us a window to stop Pub/Sub consumers cleanly while Hazelcast is still running.
     *
     * Routes are stopped first so in-flight messages drain normally (acked) rather than being
     * NACKed by the shutdownTriggered check, which would cause a Pub/Sub unacked-message spike.
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        logger.info("Spring context closing - initiating graceful Pub/Sub consumer shutdown");
        stopConsumerRoutes();
        shutdownTriggered = true;
    }

    public void stopConsumerRoutes() {
        for (String routeId : PUBSUB_CONSUMER_ROUTE_IDS) {
            try {
                if (camelContext.getRoute(routeId) != null) {
                    camelContext.getRouteController().stopRoute(routeId, 10, TimeUnit.SECONDS);
                    logger.info("Stopped Pub/Sub consumer route: {}", routeId);
                }
            } catch (Exception e) {
                logger.warn("Failed to stop route {}: {}", routeId, e.getMessage());
            }
        }
    }

    public Map<String, String> getAllLocks() {
        final String lockMap = "ansharRouteLockMap";
        return hazelcastService.getHazelcastInstance().getMap(lockMap);
    }

    public String listClusterStats() {
        return hazelcastService.listNodes(true);
    }

    private void flushData(String datasetId, String dataType) {
        long t1 = System.currentTimeMillis();
        switch (dataType) {
            case "ESTIMATED_TIMETABLE":
                estimatedTimetables.clearAllByDatasetId(datasetId);
                break;
            case "VEHICLE_MONITORING":
                vehicleActivities.clearAllByDatasetId(datasetId);
                break;
            case "SITUATION_EXCHANGE":
                situations.clearAllByDatasetId(datasetId);
                break;
            case "FACILITY_MONITORING":
                facilities.clearAllByDatasetId(datasetId);
                break;
            default:
                //Ignore
        }
        logger.info("Flushing all data of type {} for {} took {} ms", dataType, datasetId, (System.currentTimeMillis()-t1));
    }

    static JSONObject mergeJsonStats(String jsonProxyStats, String jsonVmStats, String jsonEtStats, String jsonSxStats, String jsonFmStats) {
        try {
            JSONObject proxyStats = (JSONObject) new JSONParser().parse(jsonProxyStats);
            JSONObject vmStats    = (JSONObject) new JSONParser().parse(jsonVmStats);
            JSONObject etStats    = (JSONObject) new JSONParser().parse(jsonEtStats);
            JSONObject sxStats    = (JSONObject) new JSONParser().parse(jsonSxStats);
            JSONObject fmStats    = (JSONObject) new JSONParser().parse(jsonFmStats);

            mergeDataDistributionStats(proxyStats, vmStats, etStats, sxStats, fmStats);

            mergeSubscriptionStats(proxyStats, vmStats, etStats, sxStats, fmStats);

            mergePollingStats(proxyStats, vmStats, etStats, sxStats, fmStats);

            mergeOutboundSubscriptionStats(proxyStats, vmStats, etStats, sxStats, fmStats);

            mergeMetadata(proxyStats, vmStats, etStats, sxStats, fmStats);

            return proxyStats ;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void mergeMetadata(JSONObject proxyStats, JSONObject vmStats, JSONObject etStats, JSONObject sxStats, JSONObject fmStats) {
        proxyStats.put("vmServerStarted", vmStats.get("serverStarted"));
        proxyStats.put("sxServerStarted", sxStats.get("serverStarted"));
        proxyStats.put("etServerStarted", etStats.get("serverStarted"));
        proxyStats.put("fmServerStarted", fmStats.get("serverStarted"));
    }

    private static void mergeOutboundSubscriptionStats(JSONObject proxyStats, JSONObject vmStats, JSONObject etStats, JSONObject sxStats, JSONObject fmStats) {
        JSONArray outbound = (JSONArray) proxyStats.get("outbound");
        JSONArray vmOutbound = (JSONArray) vmStats.get("outbound");
        JSONArray etOutbound = (JSONArray) etStats.get("outbound");
        JSONArray sxOutbound = (JSONArray) sxStats.get("outbound");
        JSONArray fmOutbound = (JSONArray) fmStats.get("outbound");


        outbound.addAll(vmOutbound);
        outbound.addAll(etOutbound);
        outbound.addAll(sxOutbound);
        outbound.addAll(fmOutbound);
    }

    private static void mergeDataDistributionStats(JSONObject proxyStats, JSONObject vmStats, JSONObject etStats, JSONObject sxStats, JSONObject fmStats) {
        JSONObject elements = (JSONObject) proxyStats.get("elements");
        JSONObject vmElements = (JSONObject) vmStats.get("elements");
        JSONObject etElements = (JSONObject) etStats.get("elements");
        JSONObject sxElements = (JSONObject) sxStats.get("elements");
        JSONObject fmElements = (JSONObject) fmStats.get("elements");

        elements.put("vm", vmElements.get("vm"));
        elements.put("et", etElements.get("et"));
        elements.put("sx", sxElements.get("sx"));
        elements.put("fm", fmElements.get("fm"));

        JSONArray distribution = (JSONArray) elements.get("distribution");
        JSONArray vmDistribution = (JSONArray) vmElements.get("distribution");
        JSONArray etDistribution = (JSONArray) etElements.get("distribution");
        JSONArray sxDistribution = (JSONArray) sxElements.get("distribution");
        JSONArray fmDistribution = (JSONArray) fmElements.get("distribution");

        Map<String, DataCounter> combinedDistribution = new HashMap<>();
        // Populate ET-distribution
        for (Object o : etDistribution) {
            JSONObject counter = (JSONObject) o;
            String datasetId = (String) counter.get("datasetId");
            DataCounter etCounter = combinedDistribution.getOrDefault(datasetId, new DataCounter());
            etCounter.et = (Long) counter.get("etCount");
            combinedDistribution.put(datasetId, etCounter);
        }
        // Populate VM-distribution
        for (Object o : vmDistribution) {
            JSONObject counter = (JSONObject) o;
            String datasetId = (String) counter.get("datasetId");
            DataCounter vmCounter = combinedDistribution.getOrDefault(datasetId, new DataCounter());
            vmCounter.vm = (Long) counter.get("vmCount");
            combinedDistribution.put(datasetId, vmCounter);
        }
        // Populate SX-distribution
        for (Object o : sxDistribution) {
            JSONObject counter = (JSONObject) o;
            String datasetId = (String) counter.get("datasetId");
            DataCounter sxCounter = combinedDistribution.getOrDefault(datasetId, new DataCounter());
            sxCounter.sx = (Long) counter.get("sxCount");
            combinedDistribution.put(datasetId, sxCounter);
        }
        // Populate FM-distribution
        for (Object o : fmDistribution) {
            JSONObject counter = (JSONObject) o;
            String datasetId = (String) counter.get("datasetId");
            DataCounter fmCounter = combinedDistribution.getOrDefault(datasetId, new DataCounter());
            fmCounter.fm = (Long) counter.get("fmCount");
            combinedDistribution.put(datasetId, fmCounter);
        }
        // Populate combined distribution
        for (String s : combinedDistribution.keySet()) {
            DataCounter dataCounter = combinedDistribution.get(s);
            JSONObject counter = new JSONObject();
            counter.put("datasetId", s);
            counter.put("etCount", dataCounter.et);
            counter.put("vmCount", dataCounter.vm);
            counter.put("sxCount", dataCounter.sx);
            counter.put("fmCount", dataCounter.fm);
            distribution.add(counter);
        }
    }
    private static void mergeSubscriptionStats(JSONObject proxyStats, JSONObject vmStats, JSONObject etStats, JSONObject sxStats, JSONObject fmStats) {
        JSONArray subscriptionTypes = (JSONArray) proxyStats.get("types");
        JSONArray vmSubscriptionTypes = (JSONArray) vmStats.get("types");
        JSONArray etSubscriptionTypes = (JSONArray) etStats.get("types");
        JSONArray sxSubscriptionTypes = (JSONArray) sxStats.get("types");
        JSONArray fmSubscriptionTypes = (JSONArray) fmStats.get("types");

        Map<String, JSONObject> etSubscriptions = filterSubscriptions(etSubscriptionTypes, SiriDataType.ESTIMATED_TIMETABLE);
        Map<String, JSONObject> vmSubscriptions = filterSubscriptions(vmSubscriptionTypes, SiriDataType.VEHICLE_MONITORING);
        Map<String, JSONObject> sxSubscriptions = filterSubscriptions(sxSubscriptionTypes, SiriDataType.SITUATION_EXCHANGE);
        Map<String, JSONObject> fmSubscriptions = filterSubscriptions(fmSubscriptionTypes, SiriDataType.FACILITY_MONITORING);

        for (Object o : subscriptionTypes) {
            JSONObject typeObj = (JSONObject) o;

            if (typeObj.get("typeName").equals(SiriDataType.ESTIMATED_TIMETABLE.name())) {
                JSONArray subscriptions = (JSONArray) typeObj.get("subscriptions");
                for (Object et : subscriptions) {
                    JSONObject subscription = (JSONObject) et;

                    String subscriptionId = (String) subscription.get("subscriptionId");
                    JSONObject subscriptionDetail = etSubscriptions.get(subscriptionId);
                    updateSubscriptionDetails(subscription, subscriptionDetail);
                }
            }
            if (typeObj.get("typeName").equals(SiriDataType.VEHICLE_MONITORING.name())) {
                JSONArray subscriptions = (JSONArray) typeObj.get("subscriptions");
                for (Object vm : subscriptions) {
                    JSONObject subscription = (JSONObject) vm;

                    String subscriptionId = (String) subscription.get("subscriptionId");
                    JSONObject subscriptionDetail = vmSubscriptions.get(subscriptionId);
                    updateSubscriptionDetails(subscription, subscriptionDetail);
                }
            }
            if (typeObj.get("typeName").equals(SiriDataType.SITUATION_EXCHANGE.name())) {
                JSONArray subscriptions = (JSONArray) typeObj.get("subscriptions");
                for (Object sx : subscriptions) {
                    JSONObject subscription = (JSONObject) sx;

                    String subscriptionId = (String) subscription.get("subscriptionId");
                    JSONObject subscriptionDetail = sxSubscriptions.get(subscriptionId);
                    updateSubscriptionDetails(subscription, subscriptionDetail);
                }
            }
            if (typeObj.get("typeName").equals(SiriDataType.FACILITY_MONITORING.name())) {
                JSONArray subscriptions = (JSONArray) typeObj.get("subscriptions");
                for (Object fm : subscriptions) {
                    JSONObject subscription = (JSONObject) fm;

                    String subscriptionId = (String) subscription.get("subscriptionId");
                    JSONObject subscriptionDetail = fmSubscriptions.get(subscriptionId);
                    updateSubscriptionDetails(subscription, subscriptionDetail);
                }
            }

        }
    }

    private static void updateSubscriptionDetails(JSONObject subscription, JSONObject subscriptionDetail) {
        if (subscriptionDetail != null) {
            subscription.put("lastActivity", subscriptionDetail.get("lastActivity"));
            subscription.put("lastDataReceived", subscriptionDetail.get("lastDataReceived"));
            subscription.put("flagAsNotReceivingData", subscriptionDetail.get("flagAsNotReceivingData"));
            subscription.put("bytecountLabel", subscriptionDetail.get("bytecountLabel"));
            subscription.put("bytecount", subscriptionDetail.get("bytecount"));
            subscription.put("objectcount", subscriptionDetail.get("objectcount"));
            subscription.put("hitcount", subscriptionDetail.get("hitcount"));
        }
    }

    private static Map<String, JSONObject> filterSubscriptions(JSONArray subscriptionWrappers, SiriDataType dataType) {
        Map<String, JSONObject> result = new HashMap<>();
        for (Object subscriptionWrapper : subscriptionWrappers) {
            JSONObject wrapper = (JSONObject) subscriptionWrapper;
            if (wrapper.get("typeName").equals(dataType.name())) {
                JSONArray subscriptions = (JSONArray) wrapper.get("subscriptions");
                for (Object subscriptionObj : subscriptions) {
                    JSONObject subscription = (JSONObject) subscriptionObj;
                    result.put((String) subscription.get("subscriptionId"), subscription);
                }
            }
        }


        return result;
    }

    private static void mergePollingStats(JSONObject proxyStats, JSONObject vmStats, JSONObject etStats, JSONObject sxStats, JSONObject fmStats) {
        JSONArray polling = (JSONArray) proxyStats.get("polling");

        Map<String, JSONArray> remoteByType = Map.of(
                SiriDataType.ESTIMATED_TIMETABLE.name(), extractPollingForType(etStats, SiriDataType.ESTIMATED_TIMETABLE),
                SiriDataType.VEHICLE_MONITORING.name(), extractPollingForType(vmStats, SiriDataType.VEHICLE_MONITORING),
                SiriDataType.SITUATION_EXCHANGE.name(), extractPollingForType(sxStats, SiriDataType.SITUATION_EXCHANGE),
                SiriDataType.FACILITY_MONITORING.name(), extractPollingForType(fmStats, SiriDataType.FACILITY_MONITORING)
        );

        for (Object o : polling) {
            JSONObject pollingObj = (JSONObject) o;
            String typeName = (String) pollingObj.get("typeName");
            JSONArray pollingArray = (JSONArray) pollingObj.get("polling");

            JSONArray remote = remoteByType.get(typeName);
            if (remote != null) {
                // LinkedHashSet deduplicates in O(1) per element while preserving insertion order
                Set<Object> seen = new LinkedHashSet<>(pollingArray);
                seen.addAll(remote);
                pollingArray.clear();
                pollingArray.addAll(seen);
            }
        }
    }

    private static JSONArray extractPollingForType(JSONObject stats, SiriDataType type) {
        if (stats == null) {
            return new JSONArray();
        }
        JSONArray pollingTypes = (JSONArray) stats.get("polling");
        if (pollingTypes == null) {
            return new JSONArray();
        }
        for (Object o : pollingTypes) {
            JSONObject obj = (JSONObject) o;
            if (type.name().equals(obj.get("typeName"))) {
                return (JSONArray) obj.get("polling");
            }
        }
        return new JSONArray();
    }
}
class DataCounter {
    long vm, et, sx, fm;
}
