package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
public class ProductionTimetables {
    private Logger logger = LoggerFactory.getLogger(ProductionTimetables.class);

    @Autowired
    private IMap<String, ProductionTimetableDeliveryStructure> timetableDeliveries;

    @Autowired
    @Qualifier("getProductionTimetableChangesMap")
    private IMap<String, Set<String>> changesMap;
    
    /**
     * @return All vehicle activities that are still valid
     */
    public List<ProductionTimetableDeliveryStructure> getAll() {
        return new ArrayList<>(timetableDeliveries.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public List<ProductionTimetableDeliveryStructure> getAll(String datasetId) {
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
    public List<ProductionTimetableDeliveryStructure> getAllUpdates(String requestorId) {
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

    private long getExpiration(ProductionTimetableDeliveryStructure s) {

        ZonedDateTime validUntil = s.getValidUntil();
        if (validUntil != null) {
            return ZonedDateTime.now().until(validUntil, ChronoUnit.MILLIS);
        }

        return 0;
    }

    public void addAll(String datasetId, List<ProductionTimetableDeliveryStructure> ptList) {

        Set<String> changes = new HashSet<>();

        ptList.forEach(pt -> {
            String key = createKey(datasetId, pt);


            ProductionTimetableDeliveryStructure existing = timetableDeliveries.get(key);

            if (existing == null || pt.getResponseTimestamp().isAfter(existing.getResponseTimestamp())) {

                long expiration = getExpiration(pt);

                if (expiration >= 0) {
                    changes.add(key);
                    timetableDeliveries.put(key, pt, expiration, TimeUnit.MILLISECONDS);
                }
            } else {
                //Newer update has already been processed
            }
        });

        changesMap.keySet().forEach(requestor -> {
            Set<String> tmpChanges = changesMap.get(requestor);
            tmpChanges.addAll(changes);
            changesMap.put(requestor, tmpChanges);
        });
    }

    public ProductionTimetableDeliveryStructure add(String datasetId, ProductionTimetableDeliveryStructure timetableDelivery) {
        if (timetableDelivery == null) {
            return null;
        }

        List<ProductionTimetableDeliveryStructure> ptList = new ArrayList<>();
        ptList.add(timetableDelivery);
        addAll(datasetId, ptList);
        return timetableDeliveries.get(createKey(datasetId, timetableDelivery));
    }
    private String createKey(String datasetId, ProductionTimetableDeliveryStructure element) {
        StringBuffer key = new StringBuffer();

        key.append(datasetId).append(":")
                .append(element.getVersion());
        return key.toString();
    }
}
