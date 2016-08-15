package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductionTimetables extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(ProductionTimetables.class);

    private static List<ProductionTimetableDeliveryStructure> timetableDeliveries = getProdutionTimetablesList();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<ProductionTimetableDeliveryStructure> getAll() {
        removeExpiredElements();

        return timetableDeliveries;
    }


    private static void removeExpiredElements() {

        List<ProductionTimetableDeliveryStructure> itemsToRemove = new ArrayList<>();

        for (int i = 0; i < timetableDeliveries.size(); i++) {
            ProductionTimetableDeliveryStructure current = timetableDeliveries.get(i);
            if ( !isStillValid(current)) {
                itemsToRemove.add(current);
            }
        }
        timetableDeliveries.removeAll(itemsToRemove);
    }

    private static boolean isStillValid(ProductionTimetableDeliveryStructure s) {

        boolean isStillValid = false;
        ZonedDateTime validUntil = s.getValidUntil();
        //Keep if at least one is valid
        if (validUntil == null) {
            isStillValid = true;
        } else if (validUntil.isAfter(ZonedDateTime.now())) {
            isStillValid = true;
        }
        return isStillValid;
    }

    public static void add(ProductionTimetableDeliveryStructure timetableDelivery) {

        int indexToReplace = -1;
        for (int i = 0; i < timetableDeliveries.size(); i++) {
            ProductionTimetableDeliveryStructure element = timetableDeliveries.get(i);
            if (element.getVersion().equals(timetableDelivery.getVersion())) {

                //replace existing
                indexToReplace = i;
                break; //Found item to replace - no need to continue
            }
        }
        if (indexToReplace >= 0) {
            timetableDeliveries.remove(indexToReplace);
            timetableDeliveries.add(indexToReplace, timetableDelivery);
        } else {
            timetableDeliveries.add(timetableDelivery);
        }
    }
}
