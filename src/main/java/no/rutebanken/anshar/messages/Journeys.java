package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Journeys extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(Journeys.class);

    private static Map<String, EstimatedTimetableDeliveryStructure> timetableDeliveries = getJourneysMap();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedTimetableDeliveryStructure> getAll() {
        removeExpiredElements();

        return new ArrayList<>(timetableDeliveries.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedTimetableDeliveryStructure> getAll(String datasetId) {
        removeExpiredElements();

        Map<String, EstimatedTimetableDeliveryStructure> datasetIdSpecific = new HashMap<>();
        timetableDeliveries.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            EstimatedTimetableDeliveryStructure element = timetableDeliveries.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }

    private static void removeExpiredElements() {
        List<String> itemsToRemove = new ArrayList<>();
        for (String key : timetableDeliveries.keySet()) {
            EstimatedTimetableDeliveryStructure current = timetableDeliveries.get(key);
            if ( !isStillValid(current)) {
                itemsToRemove.add(key);
            }
        }

        for (String rm : itemsToRemove) {
            timetableDeliveries.remove(rm);
        }
    }

    private static boolean isStillValid(EstimatedTimetableDeliveryStructure s) {
        boolean isStillValid = false;
        ZonedDateTime validUntil = s.getValidUntil();
        //Keep if at least one is valid
        if (validUntil == null || validUntil.isAfter(ZonedDateTime.now())) {
            isStillValid = true;
        }
        return isStillValid;
    }


    public static void add(EstimatedTimetableDeliveryStructure timetableDelivery, String datasetId) {
        timetableDeliveries.put(createKey(datasetId, timetableDelivery), timetableDelivery);
    }

    private static String createKey(String datasetId, EstimatedTimetableDeliveryStructure element) {
        StringBuffer key = new StringBuffer();

        key.append(datasetId).append(":")
                .append(element.getVersion());
        return key.toString();
    }
}
