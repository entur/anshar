package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer.SEPARATOR;

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

    @Override
    public int cleanup() {
        long t1 = System.currentTimeMillis();
        Set<String> keysToRemove = new HashSet<>();
        StringBuffer b = new StringBuffer();
        timetableDeliveries.keySet()
                .stream()
                .forEach(key -> {
                    EstimatedVehicleJourney vehicleJourney = timetableDeliveries.get(key);
                    if (vehicleJourney != null) { //Object may have expired during cleanup
                        long expiration = getExpiration(vehicleJourney);
                        if (expiration < 0) {
                            b.append(key + " -> " + expiration + "/n");
                            keysToRemove.add(key);
                        }
                    }
                });

        logger.info("Cleanup removed {} expired elements in {} seconds. /n {}", keysToRemove.size(), (int)(System.currentTimeMillis()-t1)/1000, b.toString());
        keysToRemove.forEach(key -> timetableDeliveries.delete(key));
        return keysToRemove.size();
    }

    public Siri createServiceDelivery(String lineRef) {
        SortedSet<EstimatedVehicleJourney> matchingKeys = new TreeSet<>((o1, o2) -> {
            ZonedDateTime o1_firstAimedDeparture = o1.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
            ZonedDateTime o2_firstAimedDeparture = o2.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
            if (o1.getRecordedCalls() != null && o1.getRecordedCalls().getRecordedCalls() != null) {
                if (o1.getRecordedCalls().getRecordedCalls().size() > 0) {
                    o1_firstAimedDeparture = o1.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
                }
            }
            if (o2.getRecordedCalls() != null && o2.getRecordedCalls().getRecordedCalls() != null) {
                if (o2.getRecordedCalls().getRecordedCalls().size() > 0) {
                    o2_firstAimedDeparture = o2.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
                }
            }

            return o1_firstAimedDeparture.compareTo(o2_firstAimedDeparture);
        });

        timetableDeliveries.keySet()
                .stream()
                .forEach(key -> {
                    EstimatedVehicleJourney vehicleJourney = timetableDeliveries.get(key);
                    if (vehicleJourney != null) { //Object may have expired
                        if (vehicleJourney.getLineRef() != null &&
                                (vehicleJourney.getLineRef().getValue().toLowerCase().startsWith(lineRef.toLowerCase() + SEPARATOR) |
                                vehicleJourney.getLineRef().getValue().toLowerCase().endsWith(SEPARATOR + lineRef.toLowerCase())|
                                vehicleJourney.getLineRef().getValue().equalsIgnoreCase(lineRef))
                                ) {
                            matchingKeys.add(vehicleJourney);
                        }
                    }
                });

        return siriObjectFactory.createETServiceDelivery(matchingKeys);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
        }

        // Get all relevant ids
        Set<String> allIds = new HashSet<>();
        Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            timetableDeliveries.keySet().stream()
                    .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                    .forEach(key -> idSet.add(key));
        }

        lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriodMinutes, TimeUnit.MINUTES);

        //Filter by datasetId
        Set<String> collectedIds = idSet.stream()
                .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                .limit(maxSize)
                .collect(Collectors.toSet());

        //Remove collected objects
        collectedIds.forEach(id -> idSet.remove(id));


        logger.info("Returning {}, {} left for requestorRef {}", collectedIds.size(), idSet.size(), requestorId);

        Boolean isMoreData = !idSet.isEmpty();

        //Update change-tracker
        changesMap.set(requestorId, idSet);

        Collection<EstimatedVehicleJourney> values = timetableDeliveries.getAll(collectedIds).values();
        Siri siri = siriObjectFactory.createETServiceDelivery(values);

        siri.getServiceDelivery().setMoreData(isMoreData);
        MessageRefStructure msgRef = new MessageRefStructure();
        msgRef.setValue(requestorId);
        siri.getServiceDelivery().setRequestMessageRef(msgRef);
        return siri;
    }

    public Collection<EstimatedVehicleJourney> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriodMinutes, TimeUnit.MINUTES);

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
                changesMap.set(requestorId, existingSet);


                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.info("Returning all to requestorRef {}", requestorId);
                changesMap.set(requestorId, new HashSet<>());
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
            if (vehicleJourney.getRecordedCalls() != null && !vehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
                List<RecordedCall> recordedCalls = vehicleJourney.getRecordedCalls().getRecordedCalls();
                RecordedCall lastRecordedCall = recordedCalls.get(recordedCalls.size() - 1);

                if (lastRecordedCall.getAimedArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getAimedArrivalTime();
                }
                if (lastRecordedCall.getAimedDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getAimedDepartureTime();
                }
                if (lastRecordedCall.getExpectedArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getExpectedArrivalTime();
                }
                if (lastRecordedCall.getExpectedDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getExpectedDepartureTime();
                }

            }
            if (vehicleJourney.getEstimatedCalls() != null && !vehicleJourney.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
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
            return -1;
        }
    }


    public Collection<EstimatedVehicleJourney> addAll(String datasetId, List<EstimatedVehicleJourney> etList) {

        Set<String> changes = new HashSet<>();

        Counter outdatedCounter = new CounterImpl(0);

        etList.forEach(et -> {
            String key = createKey(datasetId, et);

            EstimatedVehicleJourney existing = null;
            if (!timetableDeliveries.containsKey(key)){
                mapFutureRecordedCallsToEstimatedCalls(et);
            } else {
                existing = timetableDeliveries.get(key);
            }

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


            if (keep) {
                if (et.isIsCompleteStopSequence() != null && !et.isIsCompleteStopSequence()) {
                    //Not complete - merge partial update into existing
                    if (existing != null) {
                        EstimatedVehicleJourney.EstimatedCalls existingEstimatedCallWrapper = existing.getEstimatedCalls();
                        EstimatedVehicleJourney.EstimatedCalls updatedEstimatedCallWrapper = et.getEstimatedCalls();

                        EstimatedVehicleJourney.RecordedCalls existingRecordedCallWrapper = existing.getRecordedCalls();
                        if (existingRecordedCallWrapper == null) {
                            existingRecordedCallWrapper = new EstimatedVehicleJourney.RecordedCalls();
                            existing.setRecordedCalls(existingRecordedCallWrapper);
                        }
                        EstimatedVehicleJourney.RecordedCalls updatedRecordedCallWrapper = et.getRecordedCalls();

                        LinkedHashMap<String, RecordedCall> recordedCallsMap = new LinkedHashMap<>();
                        LinkedHashMap<String, EstimatedCall> estimatedCallsMap = new LinkedHashMap<>();

                        // Merge existing and updated RecordedCalls
                        if (existingRecordedCallWrapper != null && existingRecordedCallWrapper.getRecordedCalls() != null ) {
                            for (RecordedCall recordedCall : existingRecordedCallWrapper.getRecordedCalls()) {
                                if (recordedCall.getStopPointRef() != null) {
                                    recordedCallsMap.put(getOriginalId(recordedCall.getStopPointRef().getValue()), recordedCall);
                                }
                            }
                        }
                        if (updatedRecordedCallWrapper != null && updatedRecordedCallWrapper.getRecordedCalls() != null ) {
                            for (RecordedCall recordedCall : updatedRecordedCallWrapper.getRecordedCalls()) {
                                if (recordedCall.getStopPointRef() != null) {
                                    recordedCallsMap.put(getOriginalId(recordedCall.getStopPointRef().getValue()), recordedCall);
                                }
                            }
                        }

                        //Keep estimatedCalls not in RecordedCalls
                        if (existingEstimatedCallWrapper != null && existingEstimatedCallWrapper.getEstimatedCalls() != null ) {
                            for (EstimatedCall call : existingEstimatedCallWrapper.getEstimatedCalls()) {
                                if (!recordedCallsMap.containsKey(call.getStopPointRef().getValue())) {
                                    estimatedCallsMap.put(getOriginalId(call.getStopPointRef().getValue()), call);
                                }
                            }
                        }
                        //Add or replace existing calls
                        if (updatedEstimatedCallWrapper != null && updatedEstimatedCallWrapper.getEstimatedCalls() != null ) {
                            for (EstimatedCall call : updatedEstimatedCallWrapper.getEstimatedCalls()) {
                                estimatedCallsMap.put(getOriginalId(call.getStopPointRef().getValue()), call);
                            }
                        }

                        EstimatedVehicleJourney.EstimatedCalls joinedEstimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
                        joinedEstimatedCalls.getEstimatedCalls().addAll(estimatedCallsMap.values());

                        EstimatedVehicleJourney.RecordedCalls joinedRecordedCalls = new EstimatedVehicleJourney.RecordedCalls();
                        joinedRecordedCalls.getRecordedCalls().addAll(recordedCallsMap.values());

                        et.setEstimatedCalls(joinedEstimatedCalls);
                        et.setRecordedCalls(joinedRecordedCalls);
                    }
                }

                if (existing != null && existing.isIsCompleteStopSequence() != null) {
                    //If updates are merged in, the journey is still complete...
                    et.setIsCompleteStopSequence(existing.isIsCompleteStopSequence());
                }

                long expiration = getExpiration(et);
                if (expiration > 0) {
                    //Ignoring elements without EstimatedCalls
                    if (et.getEstimatedCalls() != null &&
                            et.getEstimatedCalls().getEstimatedCalls() != null &&
                            !et.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
                        changes.add(key);
                        timetableDeliveries.set(key, et, expiration, TimeUnit.MILLISECONDS);
                    }
                } else {
                    outdatedCounter.increment();
                }

            }
        });

        logger.info("Updated {} (of {})", changes.size(), etList.size());

        changesMap.keySet().forEach(requestor -> {
            if (lastUpdateRequested.get(requestor) != null) {
                Set<String> tmpChanges = changesMap.get(requestor);
                tmpChanges.addAll(changes);
                changesMap.set(requestor, tmpChanges);
            } else {
                changesMap.remove(requestor);
            }
        });

        return timetableDeliveries.getAll(changes).values();
    }


    /**
     * Temporary fix to handle future VehicleJourneys where all stations are put in RecordedCalls
     *
     * This is necessary because train-operators need to flag trains as "arrived" to keep information-displays correct.
     *
     * Follow up is registered in NRP-2286
     *
     * @param et
     */
    private void mapFutureRecordedCallsToEstimatedCalls(EstimatedVehicleJourney et) {
        if (et.getRecordedCalls() != null && et.getRecordedCalls().getRecordedCalls() != null && et.getRecordedCalls().getRecordedCalls().size() > 0 &&
                (et.getEstimatedCalls() == null || (et.getEstimatedCalls().getEstimatedCalls() != null && et.getEstimatedCalls().getEstimatedCalls().size() == 0))) {

            List<RecordedCall> predictedRecordedCalls = et.getRecordedCalls().getRecordedCalls();
            List<RecordedCall> recordedCalls = new ArrayList<>();
            List<EstimatedCall> estimatedCalls = new ArrayList<>();
            boolean estimatedCallsFromHere = false;
            for (RecordedCall recordedCall : predictedRecordedCalls) {

                if (estimatedCallsFromHere ||
                        (recordedCall.getAimedDepartureTime() != null && recordedCall.getAimedDepartureTime().isAfter(ZonedDateTime.now())) ||
                        (recordedCall.getExpectedDepartureTime() != null && recordedCall.getExpectedDepartureTime().isAfter(ZonedDateTime.now())) ||
                        (recordedCall.getAimedArrivalTime() != null && recordedCall.getAimedArrivalTime().isAfter(ZonedDateTime.now())) ||
                        (recordedCall.getExpectedArrivalTime() != null && recordedCall.getExpectedArrivalTime().isAfter(ZonedDateTime.now()))
                        ) {

                    //When the first estimatedCall is discovered - all remaining calls should be added as estimated
                    estimatedCallsFromHere = true;

                    EstimatedCall call = new EstimatedCall();

                    call.setStopPointRef(recordedCall.getStopPointRef());
                    call.getStopPointNames().addAll(recordedCall.getStopPointNames());
                    call.setOrder(recordedCall.getOrder());
                    call.setVisitNumber(recordedCall.getVisitNumber());

                    call.setAimedArrivalTime(recordedCall.getAimedArrivalTime());
                    call.setExpectedArrivalTime(recordedCall.getExpectedArrivalTime());
                    if (call.getExpectedArrivalTime() == null) {
                        call.setExpectedArrivalTime(recordedCall.getActualArrivalTime());
                    }

                    call.setArrivalPlatformName(recordedCall.getArrivalPlatformName());

                    call.setAimedDepartureTime(recordedCall.getAimedDepartureTime());
                    call.setExpectedDepartureTime(recordedCall.getExpectedDepartureTime());
                    if (call.getExpectedDepartureTime() == null) {
                        call.setExpectedDepartureTime(recordedCall.getActualDepartureTime());
                    }
                    call.setDeparturePlatformName(recordedCall.getDeparturePlatformName());

                    estimatedCalls.add(call);
                } else {
                    recordedCalls.add(recordedCall);
                }
            }
            if (estimatedCalls.size() > 0) {
                logger.warn("Remapped {} RecordedCalls to {} RecordedCalls and {} EstimatedCalls for Line: {}, VehicleRef: {}",
                        predictedRecordedCalls.size(), recordedCalls.size(), estimatedCalls.size(),
                        (et.getLineRef() != null ? et.getLineRef().getValue():null), (et.getVehicleRef() != null ? et.getVehicleRef().getValue():null));
            }
            et.getRecordedCalls().getRecordedCalls().clear();
            et.getRecordedCalls().getRecordedCalls().addAll(recordedCalls);
            et.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());
            et.getEstimatedCalls().getEstimatedCalls().addAll(estimatedCalls);
        }
    }

    private String getOriginalId(String stopPointRef) {
        if (stopPointRef != null && stopPointRef.indexOf(SEPARATOR) > 0) {
            return stopPointRef.substring(0, stopPointRef.indexOf(SEPARATOR));
        }
        return stopPointRef;
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
