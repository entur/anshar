package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;

import java.time.ZonedDateTime;
import java.util.*;

public class EstimatedTimetables extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    private static Map<String, EstimatedVehicleJourney> timetableDeliveries = getJourneysMap();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedVehicleJourney> getAll() {
        removeExpiredElements();

        return new ArrayList<>(timetableDeliveries.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedVehicleJourney> getAll(String datasetId) {
        removeExpiredElements();

        Map<String, EstimatedVehicleJourney> datasetIdSpecific = new HashMap<>();
        timetableDeliveries.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            EstimatedVehicleJourney element = timetableDeliveries.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }

    private static void removeExpiredElements() {
        List<String> itemsToRemove = new ArrayList<>();
        for (String key : timetableDeliveries.keySet()) {
            EstimatedVehicleJourney current = timetableDeliveries.get(key);
            if ( !isStillValid(current)) {
                itemsToRemove.add(key);
            }
        }

        for (String rm : itemsToRemove) {
            timetableDeliveries.remove(rm);
        }
    }

    private static boolean isStillValid(EstimatedVehicleJourney vehicleJourney) {
        boolean isStillValid = false;
        if (vehicleJourney != null) {
            if (vehicleJourney.getEstimatedCalls() != null) {
                List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
                EstimatedCall lastEstimatedCall = estimatedCalls.get(estimatedCalls.size() - 1);

                ZonedDateTime aimedArrivalTime = lastEstimatedCall.getAimedArrivalTime();
                ZonedDateTime expectedArrivalTime = lastEstimatedCall.getExpectedArrivalTime();

                //If vehicle arrived at its last stop more than a day ago - remove
                if (expectedArrivalTime != null) {
                    return expectedArrivalTime.isAfter(ZonedDateTime.now().minusDays(1));
                }
                if (aimedArrivalTime != null) {
                    return aimedArrivalTime.isAfter(ZonedDateTime.now().minusDays(1));
                }

            }
        }
        return isStillValid;
    }


    public static EstimatedVehicleJourney add(EstimatedVehicleJourney timetableDelivery, String datasetId) {
        if (timetableDelivery == null) {return null;}
        EstimatedVehicleJourney previous = timetableDeliveries.put(createKey(datasetId, timetableDelivery), timetableDelivery);
        if (previous != null) {
            // TODO: Determine if data is updated?
            // Currently assumes that unique key is enough to replace/update data
        }
        return previous;
    }

    private static String createKey(String datasetId, EstimatedVehicleJourney element) {
        StringBuffer key = new StringBuffer();

        key.append(datasetId).append(":")
                .append((element.getOperatorRef() != null ? element.getOperatorRef().getValue() : "null"))
                .append(":")
                .append((element.getLineRef() != null ? element.getLineRef().getValue() : "null"))
                .append(":")
                .append((element.getVehicleRef() != null ? element.getVehicleRef().getValue() : "null"))
                .append(":")
                .append((element.getDirectionRef() != null ? element.getDirectionRef().getValue() :"null"))
                .append(":")
                .append((element.getDatedVehicleJourneyRef() != null ? element.getDatedVehicleJourneyRef().getValue() :"null"));

        return key.toString();
    }
}
