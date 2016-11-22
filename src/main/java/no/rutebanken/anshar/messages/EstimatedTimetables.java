package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.messages.collections.ExpiringConcurrentMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;

import java.time.ZonedDateTime;
import java.util.*;

public class EstimatedTimetables {
    private static Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    static ExpiringConcurrentMap<String, EstimatedVehicleJourney> timetableDeliveries;

    static ExpiringConcurrentMap<String, Set<String>> changesMap;

    static {
        DistributedCollection dc = new DistributedCollection();
        timetableDeliveries = dc.getEstimatedTimetablesMap();
        changesMap = dc.getEstimatedTimetableChangesMap();
    }


    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedVehicleJourney> getAll() {
        return new ArrayList<>(timetableDeliveries.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedVehicleJourney> getAllUpdates(String requestorId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            if (idSet != null) {
                List<EstimatedVehicleJourney> changes = new ArrayList<>();

                idSet.stream().forEach(key -> {
                    EstimatedVehicleJourney element = timetableDeliveries.get(key);
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

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<EstimatedVehicleJourney> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }
        Map<String, EstimatedVehicleJourney> datasetIdSpecific = new HashMap<>();
        timetableDeliveries.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            EstimatedVehicleJourney element = timetableDeliveries.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }


    @Nullable
    private static ZonedDateTime getExpiration(EstimatedVehicleJourney vehicleJourney) {
        ZonedDateTime expiryTimestamp = null;
        if (vehicleJourney != null) {
            if (vehicleJourney.getEstimatedCalls() != null) {
                List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
                EstimatedCall lastEstimatedCall = estimatedCalls.get(estimatedCalls.size() - 1);

                ZonedDateTime aimedArrivalTime = lastEstimatedCall.getAimedArrivalTime();
                ZonedDateTime expectedArrivalTime = lastEstimatedCall.getExpectedArrivalTime();

                if (expectedArrivalTime != null) {
                    expiryTimestamp = expectedArrivalTime;
                }
                if (aimedArrivalTime != null) {
                    expiryTimestamp = aimedArrivalTime;
                }

            }
        }

        return expiryTimestamp;
    }


    public static EstimatedVehicleJourney add(EstimatedVehicleJourney delivery, String datasetId) {
        if (delivery == null) {return null;}

        String key = createKey(datasetId, delivery);
        ZonedDateTime expiration = getExpiration(delivery);
        if (expiration != null && expiration.isBefore(ZonedDateTime.now())) {
            //Ignore elements that have already expired
            return null;
        }


        if (delivery.isIsCompleteStopSequence() != null && !delivery.isIsCompleteStopSequence()) {
            //Not complete - calculate partial update
            EstimatedVehicleJourney existing = timetableDeliveries.get(key);
            if (existing != null) {
                EstimatedVehicleJourney.EstimatedCalls existingCallWrapper = existing.getEstimatedCalls();
                EstimatedVehicleJourney.EstimatedCalls updatedCallWrapper = delivery.getEstimatedCalls();

                SortedMap<Integer, EstimatedCall> joinedCallsMap = new TreeMap<>();

                //Existing calls
                for (EstimatedCall call : existingCallWrapper.getEstimatedCalls()) {
                    //Assuming that either Visitnumber or Order is always used
                    int order = (call.getVisitNumber() != null ? call.getVisitNumber():call.getOrder()).intValue();
                    joinedCallsMap.put(order, call);
                }
                //Add or replace existing calls
                for (EstimatedCall call : updatedCallWrapper.getEstimatedCalls()) {
                    int order = (call.getVisitNumber() != null ? call.getVisitNumber():call.getOrder()).intValue();
                    joinedCallsMap.put(order, call);
                }

                EstimatedVehicleJourney.EstimatedCalls joinedCalls = new EstimatedVehicleJourney.EstimatedCalls();
                joinedCalls.getEstimatedCalls().addAll(joinedCallsMap.values());

                delivery.setEstimatedCalls(joinedCalls);
            }
        }


        changesMap.keySet().forEach(requestor -> {
            Set<String> changes = changesMap.get(requestor);
            changes.add(key);
            changesMap.put(requestor, changes);
        });

        EstimatedVehicleJourney previous = timetableDeliveries.put(key, delivery, expiration);

        if (previous != null) {
            // TODO: Determine if data is updated?
            // Currently assumes that unique key is enough to replace/update data
            return previous;
        } else {
            // new element
            return delivery;
        }
    }

    private static String createKey(String datasetId, EstimatedVehicleJourney element) {
        StringBuffer key = new StringBuffer();

        String datedVehicleJourney = element.getDatedVehicleJourneyRef() != null ? element.getDatedVehicleJourneyRef().getValue() : null;
        if (datedVehicleJourney == null && element.getFramedVehicleJourneyRef() != null) {
            String dataFrameRef = element.getFramedVehicleJourneyRef().getDataFrameRef() != null ? element.getFramedVehicleJourneyRef().getDataFrameRef().getValue():"null";
            datedVehicleJourney = dataFrameRef + ":" + element.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        }

        key.append(datasetId).append(":")
                .append((element.getOperatorRef() != null ? element.getOperatorRef().getValue() : "null"))
                .append(":")
                .append((element.getLineRef() != null ? element.getLineRef().getValue() : "null"))
                .append(":")
                .append((element.getVehicleRef() != null ? element.getVehicleRef().getValue() : "null"))
                .append(":")
                .append((element.getDirectionRef() != null ? element.getDirectionRef().getValue() :"null"))
                .append(":")
                .append(datedVehicleJourney);

        return key.toString();
    }
}
