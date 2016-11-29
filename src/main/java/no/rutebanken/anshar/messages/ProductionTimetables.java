package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.messages.collections.ExpiringConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.*;

public class ProductionTimetables {
    private static Logger logger = LoggerFactory.getLogger(ProductionTimetables.class);

    static ExpiringConcurrentMap<String, ProductionTimetableDeliveryStructure> timetableDeliveries;
    static ExpiringConcurrentMap<String, Set<String>> changesMap;

    static {
        DistributedCollection dc = new DistributedCollection();
        timetableDeliveries = dc.getProductionTimetablesMap();
        changesMap = dc.getProductionTimetableChangesMap();
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<ProductionTimetableDeliveryStructure> getAll() {
        return new ArrayList<>(timetableDeliveries.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<ProductionTimetableDeliveryStructure> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }        
        Map<String, ProductionTimetableDeliveryStructure> datasetIdSpecific = new HashMap<>();
        timetableDeliveries.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            ProductionTimetableDeliveryStructure element = timetableDeliveries.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }


    /**
     * @return All vehicle activities that are still valid
     */
    public static List<ProductionTimetableDeliveryStructure> getAllUpdates(String requestorId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            if (idSet != null) {
                List<ProductionTimetableDeliveryStructure> changes = new ArrayList<>();

                idSet.stream().forEach(key -> {
                    ProductionTimetableDeliveryStructure element = timetableDeliveries.get(key);
                    if (element != null) {
                        changes.add(element);
                    }
                });
                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                existingSet.removeAll(idSet);
                changesMap.put(requestorId, existingSet);
                return changes;
            } else {
                changesMap.put(requestorId, new HashSet<>());
            }
        }

        return getAll();
    }

    private static ZonedDateTime getExpiration(ProductionTimetableDeliveryStructure s) {

        return s.getValidUntil();
    }

    public static void addAll(String datasetId, List<ProductionTimetableDeliveryStructure> ptList) {

        Map< String, ProductionTimetableDeliveryStructure> updates = new HashMap<>();
        Map<String, ZonedDateTime> expiries = new HashMap<>();
        Set<String> changes = new HashSet<>();

        ptList.forEach(pt -> {
            String key = createKey(datasetId, pt);


            ProductionTimetableDeliveryStructure existing = timetableDeliveries.get(key);
            if (existing == null || pt.getResponseTimestamp().isAfter(existing.getResponseTimestamp())) {
                ZonedDateTime expiration = getExpiration(pt);

                if (expiration != null && expiration.isAfter(ZonedDateTime.now())) {
                    changes.add(key);
                    updates.put(key, pt);
                    expiries.put(key, expiration);
                }
            } else {
                //Newer update has already been processed
            }
        });

        ProductionTimetables.timetableDeliveries.putAll(updates, expiries);

        changesMap.keySet().forEach(requestor -> {
            Set<String> tmpChanges = changesMap.get(requestor);
            tmpChanges.addAll(changes);
            changesMap.put(requestor, tmpChanges);
        });
    }

    public static ProductionTimetableDeliveryStructure add(String datasetId, ProductionTimetableDeliveryStructure timetableDelivery) {
        if (timetableDelivery == null) {
            return null;
        }

        List<ProductionTimetableDeliveryStructure> situations = new ArrayList<>();
        situations.add(timetableDelivery);
        addAll(datasetId, situations);
        return ProductionTimetables.timetableDeliveries.get(createKey(datasetId, timetableDelivery));
    }
    private static String createKey(String datasetId, ProductionTimetableDeliveryStructure element) {
        StringBuffer key = new StringBuffer();

        key.append(datasetId).append(":")
                .append(element.getVersion());
        return key.toString();
    }
}
