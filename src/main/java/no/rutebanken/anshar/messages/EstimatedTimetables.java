package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
public class EstimatedTimetables {
    private Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    @Autowired
    private IMap<String, EstimatedVehicleJourney> timetableDeliveries;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<String>> changesMap;

    /**
     * @return All vehicle activities
     */
    public List<EstimatedVehicleJourney> getAll() {
        return new ArrayList<>(timetableDeliveries.values());
    }

    public int getSize() {
        return timetableDeliveries.size();
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public List<EstimatedVehicleJourney> getAllUpdates(String requestorId) {
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
    public List<EstimatedVehicleJourney> getAll(String datasetId) {
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

    private static long getExpiration(EstimatedVehicleJourney vehicleJourney) {
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

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp, ChronoUnit.MILLIS);
        } else {
            return 0;
        }
    }


    public void addAll(String datasetId, List<EstimatedVehicleJourney> etList) {

        Set<String> changes = new HashSet<>();

        etList.forEach(et -> {
            String key = createKey(datasetId, et);

            EstimatedVehicleJourney existing = timetableDeliveries.get(key);

            boolean keep = (existing == null); //No existing data - keep
            if (existing != null &&
                    (et.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {
                //Newer data has already been processed
                keep = et.getRecordedAtTime().isAfter(existing.getRecordedAtTime());
            }

            long expiration = getExpiration(et);
            if (expiration >= 0 && keep) {
                if (et.isIsCompleteStopSequence() != null && !et.isIsCompleteStopSequence()) {
                    //Not complete - merge partial update into existing
                    if (existing != null) {
                        EstimatedVehicleJourney.EstimatedCalls existingCallWrapper = existing.getEstimatedCalls();
                        EstimatedVehicleJourney.EstimatedCalls updatedCallWrapper = et.getEstimatedCalls();

                        SortedMap<Integer, EstimatedCall> joinedCallsMap = new TreeMap<>();

                        //Existing calls
                        for (EstimatedCall call : existingCallWrapper.getEstimatedCalls()) {
                            //Assuming that either Visitnumber or Order is always used
                            int order = (call.getVisitNumber() != null ? call.getVisitNumber() : call.getOrder()).intValue();
                            joinedCallsMap.put(order, call);
                        }
                        //Add or replace existing calls
                        for (EstimatedCall call : updatedCallWrapper.getEstimatedCalls()) {
                            int order = (call.getVisitNumber() != null ? call.getVisitNumber() : call.getOrder()).intValue();
                            joinedCallsMap.put(order, call);
                        }

                        EstimatedVehicleJourney.EstimatedCalls joinedCalls = new EstimatedVehicleJourney.EstimatedCalls();
                        joinedCalls.getEstimatedCalls().addAll(joinedCallsMap.values());

                        et.setEstimatedCalls(joinedCalls);
                    }
                }
                changes.add(key);
                timetableDeliveries.put(key, et, expiration, TimeUnit.MILLISECONDS);
            }
        });

        changesMap.keySet().forEach(requestor -> {
            Set<String> tmpChanges = changesMap.get(requestor);
            tmpChanges.addAll(changes);
            changesMap.put(requestor, tmpChanges);
        });
    }


    EstimatedVehicleJourney add(String datasetId, EstimatedVehicleJourney delivery) {
        if (delivery == null) {return null;}

        List<EstimatedVehicleJourney> deliveries = new ArrayList<>();
        deliveries.add(delivery);
        addAll(datasetId, deliveries);
        return timetableDeliveries.get(createKey(datasetId, delivery));
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
