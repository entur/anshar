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

    public static ProductionTimetableDeliveryStructure add(ProductionTimetableDeliveryStructure timetableDelivery, String datasetId) {
        if (timetableDelivery == null) {
            return null;
        }
        String key = createKey(datasetId, timetableDelivery);

        ZonedDateTime expiration = getExpiration(timetableDelivery);
        if (expiration != null && expiration.isBefore(ZonedDateTime.now())) {
            //Ignore elements that have already expired
            return null;
        }


        changesMap.keySet().forEach(requestor -> {
            Set<String> changes = changesMap.get(requestor);
            changes.add(key);
            changesMap.put(requestor, changes);
        });

        ProductionTimetableDeliveryStructure previous = timetableDeliveries.put(key, timetableDelivery, getExpiration(timetableDelivery));
        if (previous != null) {
            /*
             * TODO: How to determine if PT-element has been updated?
             */
            if (!timetableDelivery.equals(previous)) {
                return timetableDelivery;
            }
        }
        return null;
    }
    private static String createKey(String datasetId, ProductionTimetableDeliveryStructure element) {
        StringBuffer key = new StringBuffer();

        key.append(datasetId).append(":")
                .append(element.getVersion());
        return key.toString();
    }
}
