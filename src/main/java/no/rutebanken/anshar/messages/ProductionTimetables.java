package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductionTimetables extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(ProductionTimetables.class);

    private static Map<String, ProductionTimetableDeliveryStructure> timetableDeliveries = getProductionTimetablesMap();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<ProductionTimetableDeliveryStructure> getAll() {
        removeExpiredElements();

        return new ArrayList<>(timetableDeliveries.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<ProductionTimetableDeliveryStructure> getAll(String vendor) {
        removeExpiredElements();

        Map<String, ProductionTimetableDeliveryStructure> vendorSpecific = new HashMap<>();
        timetableDeliveries.keySet().stream().filter(key -> key.startsWith(vendor + ":")).forEach(key -> {
            ProductionTimetableDeliveryStructure element = timetableDeliveries.get(key);
            if (element != null) {
                vendorSpecific.put(key, element);
            }
        });

        return new ArrayList<>(vendorSpecific.values());
    }

    private static void removeExpiredElements() {

        List<String> itemsToRemove = new ArrayList<>();

        for (String key : timetableDeliveries.keySet()) {
            ProductionTimetableDeliveryStructure current = timetableDeliveries.get(key);
            if ( !isStillValid(current)) {
                itemsToRemove.add(key);
            }
        }

        for (String rm : itemsToRemove) {
            timetableDeliveries.remove(rm);
        }
    }

    private static boolean isStillValid(ProductionTimetableDeliveryStructure s) {

        boolean isStillValid = false;
        ZonedDateTime validUntil = s.getValidUntil();
        //Keep if at least one is valid
        if (validUntil == null || validUntil.isAfter(ZonedDateTime.now())) {
            isStillValid = true;
        }
        return isStillValid;
    }

    public static void add(ProductionTimetableDeliveryStructure timetableDelivery, String vendor) {
        timetableDeliveries.put(createKey(vendor, timetableDelivery), timetableDelivery);
    }
    private static String createKey(String vendor, ProductionTimetableDeliveryStructure element) {
        StringBuffer key = new StringBuffer();

        key.append(vendor).append(":")
                .append(element.getVersion());
        return key.toString();
    }
}
