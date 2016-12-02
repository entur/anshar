package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.PtSituationElement;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
public class Situations {
    private Logger logger = LoggerFactory.getLogger(Situations.class);

    @Autowired
    private IMap<String,PtSituationElement> situations;

    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<String>> changesMap;

    /**
     * @return All situations that are still valid
     */
    public List<PtSituationElement> getAll() {
        return new ArrayList<>(situations.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public List<PtSituationElement> getAll(String datasetId) {
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
    public List<PtSituationElement> getAllUpdates(String requestorId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            if (idSet != null) {
                List<PtSituationElement> changes = new ArrayList<>();

                idSet.stream().forEach(key -> {
                    PtSituationElement element = situations.get(key);
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

    private long getExpiration(PtSituationElement situationElement) {
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
            return 0;
        }
    }

    public void addAll(String datasetId, List<PtSituationElement> sxList) {
        Map< String, PtSituationElement> updates = new HashMap<>();
        Map<String, ZonedDateTime> expiries = new HashMap<>();
        Set<String> changes = new HashSet<>();

        sxList.forEach(situation -> {
            String key = createKey(datasetId, situation);

            //TODO: Determine if newer situation has already been handled

            changes.add(key);
            situations.put(key, situation, getExpiration(situation), TimeUnit.MILLISECONDS);

        });

        changesMap.keySet().forEach(requestor -> {
            Set<String> tmpChanges = changesMap.get(requestor);
            tmpChanges.addAll(changes);
            changesMap.put(requestor, tmpChanges);
        });
    }

    PtSituationElement add(String datasetId, PtSituationElement situation) {
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
