package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.collections.ExpiringConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.anshar.messages.collections.DistributedCollection.getProductionTimetablesMap;

public class ProductionTimetables {
    private static Logger logger = LoggerFactory.getLogger(ProductionTimetables.class);

    static ExpiringConcurrentMap<String, ProductionTimetableDeliveryStructure> timetableDeliveries = getProductionTimetablesMap();
    static ExpiringConcurrentMap<String, Set<String>> changesMap = new ExpiringConcurrentMap<>(new ConcurrentHashMap<>(), 30, 300);

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
            changesMap.put(requestorId, new HashSet<>());
            if (idSet != null) {
                List<ProductionTimetableDeliveryStructure> changes = new ArrayList<>();

                idSet.stream().forEach(key -> {
                    ProductionTimetableDeliveryStructure element = timetableDeliveries.get(key);
                    if (element != null) {
                        changes.add(element);
                    }
                });
                return changes;
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

        changesMap.keySet().forEach(requestor -> {
            Set<String> ids = changesMap.get(requestor);
            ids.add(key);
            changesMap.put(requestor, ids);
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
