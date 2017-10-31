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
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.MessageRefStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class Situations implements SiriRepository<PtSituationElement> {
    private Logger logger = LoggerFactory.getLogger(Situations.class);

    @Autowired
    private IMap<String,PtSituationElement> situations;

    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<String>> changesMap;


    @Autowired
    @Qualifier("getLastSxUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    /**
     * @return All situations
     */
    public Collection<PtSituationElement> getAll() {
        return situations.values();
    }

    public int getSize() {
        return situations.size();
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
        }

        // Get all relevant ids
        Set<String> allIds = new HashSet<>();
        Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            situations.keySet().forEach(key -> idSet.add(key));
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

        Collection<PtSituationElement> values = situations.getAll(collectedIds).values();
        Siri siri = siriObjectFactory.createSXServiceDelivery(values);

        siri.getServiceDelivery().setMoreData(isMoreData);

        MessageRefStructure msgRef = new MessageRefStructure();
        msgRef.setValue(requestorId);
        siri.getServiceDelivery().setRequestMessageRef(msgRef);

        return siri;
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<PtSituationElement> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        Map<String, PtSituationElement> datasetIdSpecific = new HashMap<>();
        situations.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            PtSituationElement element = situations.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }


    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<PtSituationElement> getAllUpdates(String requestorId, String datasetId) {
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
                Collection<PtSituationElement> changes = situations.getAll(datasetFilteredIdSet).values();

                // Data may have been updated
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
            }
            changesMap.set(requestorId, new HashSet<>());
        }

        return getAll(datasetId);
    }

    @Override
    public int cleanup() {
        long t1 = System.currentTimeMillis();
        Set<String> keysToRemove = new HashSet<>();
        situations.keySet()
                .stream()
                .forEach(key -> {
                    PtSituationElement situationElement = situations.get(key);
                    if (situationElement != null) {
                        long expiration = getExpiration(situationElement);
                        if (expiration < 0) {
                            keysToRemove.add(key);
                        }
                    }
                });

        logger.info("Cleanup removed {} expired elements in {} seconds.", keysToRemove.size(), (int)(System.currentTimeMillis()-t1)/1000);
        keysToRemove.forEach(key -> situations.delete(key));
        return keysToRemove.size();
    }
    public long getExpiration(PtSituationElement situationElement) {
        List<HalfOpenTimestampOutputRangeStructure> validityPeriods = situationElement.getValidityPeriods();

        ZonedDateTime expiry = null;

        if (validityPeriods != null) {
            for (HalfOpenTimestampOutputRangeStructure validity : validityPeriods) {

                //Find latest validity
                if (expiry == null) {
                    expiry = validity.getEndTime();
                } else if (validity != null && validity.getEndTime().isAfter(expiry)) {
                    expiry = validity.getEndTime();
                }
            }
        }

        if (expiry != null) {
            return ZonedDateTime.now().until(expiry, ChronoUnit.MILLIS);
        } else {
            return -1;
        }
    }

    public Collection<PtSituationElement> addAll(String datasetId, List<PtSituationElement> sxList) {
        Set<String> changes = new HashSet<>();

        Counter alreadyExpiredCounter = new CounterImpl(0);
        sxList.forEach(situation -> {
            String key = createKey(datasetId, situation);

            //TODO: Determine if newer situation has already been handled

            long expiration = getExpiration(situation);
            if (expiration > 0) { //expiration < 0 => already expired
                situations.set(key, situation, expiration, TimeUnit.MILLISECONDS);
                changes.add(key);
            } else if (situations.containsKey(key)) {
                // Situation is no longer valid
                situations.remove(key);
            }
            if (expiration < 0) {
                alreadyExpiredCounter.increment();
            }

        });
        logger.info("Updated {} (of {}) :: Already expired: {},", changes.size(), sxList.size(), alreadyExpiredCounter.getValue());

        changesMap.keySet().forEach(requestor -> {
            if (lastUpdateRequested.get(requestor) != null) {
                Set<String> tmpChanges = changesMap.get(requestor);
                tmpChanges.addAll(changes);
                changesMap.set(requestor, tmpChanges);
            } else {
                changesMap.remove(requestor);
            }
        });
        return situations.getAll(changes).values();
    }

    public PtSituationElement add(String datasetId, PtSituationElement situation) {
        if (situation == null) {
            return null;
        }
        List<PtSituationElement> situationList = new ArrayList<>();
        situationList.add(situation);
        addAll(datasetId, situationList);
        return situations.get(createKey(datasetId, situation));
    }

    private static String createKey(String datasetId, PtSituationElement element) {
        StringBuffer key = new StringBuffer();

        key.append(datasetId).append(":")
                .append((element.getSituationNumber() != null ? element.getSituationNumber().getValue() : "null"))
                .append(":")
                .append((element.getParticipantRef() != null ? element.getParticipantRef().getValue() :"null"));
        return key.toString();
    }
}
