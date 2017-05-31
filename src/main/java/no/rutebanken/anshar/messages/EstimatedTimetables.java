package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class EstimatedTimetables  implements SiriRepository<EstimatedVehicleJourney> {
    private Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    @Autowired
    private IMap<String, EstimatedVehicleJourney> timetableDeliveries;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<String>> changesMap;

    @Autowired
    @Qualifier("getLastEtUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    /**
     * @return All ET-elements
     */
    public Collection<EstimatedVehicleJourney> getAll() {
        return timetableDeliveries.values();
    }

    public int getSize() {
        return timetableDeliveries.size();
    }

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {

        if (requestorId != null) {

            System.out.println("Returning max " + maxSize + " to requestorRef " + requestorId);
            // Get all relevant ids
            Set<String> allIds = new HashSet<>();
            Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

            if (idSet == allIds) {
                timetableDeliveries.keySet().forEach(key -> idSet.add(key));
            }

            lastUpdateRequested.put(requestorId, Instant.now(), 5, TimeUnit.MINUTES);

            //Filter by datasetId
            Set<String> collectedIds = idSet.stream()
                    .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                    .limit(maxSize)
                    .collect(Collectors.toSet());

            //Remove collected objects
            collectedIds.forEach(id -> idSet.remove(id));


            System.out.println("Returning  " + collectedIds.size() + ", " + idSet.size() + " left");

            Boolean isMoreData = !idSet.isEmpty();

            //Update change-tracker
            changesMap.put(requestorId, idSet);

            Collection<EstimatedVehicleJourney> values = timetableDeliveries.getAll(collectedIds).values();
            Siri siri = siriObjectFactory.createETServiceDelivery(values);

            siri.getServiceDelivery().setMoreData(isMoreData);
            return siri;
        } else {
            return siriObjectFactory.createETServiceDelivery(getAll(datasetId));
        }
    }

    public Collection<EstimatedVehicleJourney> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            lastUpdateRequested.put(requestorId, Instant.now(), 5, TimeUnit.MINUTES);

            if (idSet != null) {
                Set<String> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
                        datasetFilteredIdSet.add(key);
                    });
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }

                Collection<EstimatedVehicleJourney> changes = timetableDeliveries.getAll(datasetFilteredIdSet).values();

                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                existingSet.removeAll(idSet);
                changesMap.put(requestorId, existingSet);


                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.info("Returning all to requestorRef {}", requestorId);
                changesMap.put(requestorId, new HashSet<>());
            }
        }

        return getAll(datasetId);
    }

    public Collection<EstimatedVehicleJourney> getAll(String datasetId) {
        if (datasetId == null || datasetId.isEmpty()) {
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

    public long getExpiration(EstimatedVehicleJourney vehicleJourney) {
        ZonedDateTime expiryTimestamp = null;
        if (vehicleJourney != null) {
            if (vehicleJourney.getEstimatedCalls() != null) {
                List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
                EstimatedCall lastEstimatedCall = estimatedCalls.get(estimatedCalls.size() - 1);

                if (lastEstimatedCall.getAimedArrivalTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getAimedArrivalTime();
                }
                if (lastEstimatedCall.getAimedDepartureTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getAimedDepartureTime();
                }
                if (lastEstimatedCall.getExpectedArrivalTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getExpectedArrivalTime();
                }
                if (lastEstimatedCall.getExpectedDepartureTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getExpectedDepartureTime();
                }

            }
        }

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp, ChronoUnit.MILLIS);
        } else {
            return 0;
        }
    }


    public Collection<EstimatedVehicleJourney> addAll(String datasetId, List<EstimatedVehicleJourney> etList) {

        Set<String> changes = new HashSet<>();

        etList.forEach(et -> {
            String key = createKey(datasetId, et);

            EstimatedVehicleJourney existing = timetableDeliveries.get(key);

            boolean keep = false;

            if (existing != null &&
                    (et.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {

                if (et.getRecordedAtTime().isAfter(existing.getRecordedAtTime())) {
                    keep = true;
                } else {
                    logger.info("Newer data has already been processed - ignoring ET-element");
                }
            } else {
                keep = true;
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
                        if (existingCallWrapper != null && existingCallWrapper.getEstimatedCalls() != null ) {
                            for (EstimatedCall call : existingCallWrapper.getEstimatedCalls()) {
                                //Assuming that either Visitnumber or Order is always used
                                int order = (call.getVisitNumber() != null ? call.getVisitNumber() : call.getOrder()).intValue();
                                joinedCallsMap.put(order, call);
                            }
                        }
                        //Add or replace existing calls
                        if (updatedCallWrapper != null && updatedCallWrapper.getEstimatedCalls() != null ) {
                            for (EstimatedCall call : updatedCallWrapper.getEstimatedCalls()) {
                                int order = (call.getVisitNumber() != null ? call.getVisitNumber() : call.getOrder()).intValue();
                                joinedCallsMap.put(order, call);
                            }
                        }

                        EstimatedVehicleJourney.EstimatedCalls joinedCalls = new EstimatedVehicleJourney.EstimatedCalls();
                        joinedCalls.getEstimatedCalls().addAll(joinedCallsMap.values());

                        et.setEstimatedCalls(joinedCalls);
                    }
                }

                if (et.isIsCompleteStopSequence() != null && et.isIsCompleteStopSequence()) {
                    //EstimatedVehicleJourney is complete - ensure all ET-calls have order
                    int callCounter = 1;
                    if (et.getRecordedCalls() != null && et.getRecordedCalls().getRecordedCalls() != null) {
                        List<RecordedCall> recordedCalls = et.getRecordedCalls().getRecordedCalls();
                        for (int i = 0; i < recordedCalls.size(); i++) {
                            RecordedCall call = recordedCalls.get(i);
                            if (call.getOrder() == null) {
                                call.setOrder(new BigInteger("" + callCounter++));
                            } else {
                                callCounter = call.getOrder().intValue();
                            }
                        }
                    }
                    if (et.getEstimatedCalls() != null && et.getEstimatedCalls().getEstimatedCalls() != null) {
                        List<EstimatedCall> estimatedCalls = et.getEstimatedCalls().getEstimatedCalls();
                        for (int i = 0; i < estimatedCalls.size(); i++) {
                            EstimatedCall call = estimatedCalls.get(i);
                            if (call.getOrder() == null) {
                                call.setOrder(new BigInteger("" + callCounter++));
                            } else {
                                callCounter = call.getOrder().intValue();
                            }
                        }
                    }
                }

                //Ignoring elements without EstimatedCalls
                if (et.getEstimatedCalls() != null &&
                        et.getEstimatedCalls().getEstimatedCalls() != null &&
                        !et.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
                    changes.add(key);
                    timetableDeliveries.put(key, et, expiration, TimeUnit.MILLISECONDS);
                }
            }
        });

        changesMap.keySet().forEach(requestor -> {
            if (lastUpdateRequested.get(requestor) != null) {
                Set<String> tmpChanges = changesMap.get(requestor);
                tmpChanges.addAll(changes);
                changesMap.put(requestor, tmpChanges);
            } else {
                changesMap.remove(requestor);
            }
        });

        return timetableDeliveries.getAll(changes).values();
    }


    public EstimatedVehicleJourney add(String datasetId, EstimatedVehicleJourney delivery) {
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
