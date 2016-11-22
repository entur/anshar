package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.messages.collections.ExpiringConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.PtSituationElement;

import java.time.ZonedDateTime;
import java.util.*;

public class Situations {
    private static Logger logger = LoggerFactory.getLogger(Situations.class);

    static ExpiringConcurrentMap<String,PtSituationElement> situations;
    static ExpiringConcurrentMap<String, Set<String>> changesMap;

    static {
        DistributedCollection dc = new DistributedCollection();
        situations = dc.getSituationsMap();
        changesMap = dc.getSituationChangesMap();
    }
    /**
     * @return All situations that are still valid
     */
    public static List<PtSituationElement> getAll() {
        return new ArrayList<>(situations.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<PtSituationElement> getAll(String datasetId) {
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
    public static List<PtSituationElement> getAllUpdates(String requestorId) {
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

    private static ZonedDateTime getExpiration(PtSituationElement situationElement) {
        List<PtSituationElement.ValidityPeriod> validityPeriods = situationElement.getValidityPeriods();

        ZonedDateTime expiry = null;

        if (validityPeriods != null) {
            for (PtSituationElement.ValidityPeriod validity : validityPeriods) {

                //Find latest validity
                if (expiry == null) {
                    expiry = validity.getEndTime();
                } else if (validity != null && validity.getEndTime().isAfter(expiry)) {
                    expiry = validity.getEndTime();
                }
            }
        }
        return expiry;
    }

    public static void addAll(String datasetId, List<PtSituationElement> sxList) {
        Map< String, PtSituationElement> updates = new HashMap<>();
        Map<String, ZonedDateTime> expiries = new HashMap<>();
        Set<String> changes = new HashSet<>();

        sxList.forEach(situation -> {
            String key = createKey(datasetId, situation);

            ZonedDateTime expiration = getExpiration(situation);

            if (expiration != null && expiration.isAfter(ZonedDateTime.now())) {
                changes.add(key);
                updates.put(key, situation);
                expiries.put(key, expiration);
            }

        });

        Situations.situations.putAll(updates, expiries);

        changesMap.keySet().forEach(requestor -> {
            Set<String> tmpChanges = changesMap.get(requestor);
            tmpChanges.addAll(changes);
            changesMap.put(requestor, tmpChanges);
        });
    }

    static PtSituationElement add(String datasetId, PtSituationElement situation) {
        if (situation == null) {
            return null;
        }
        List<PtSituationElement> situations = new ArrayList<>();
        situations.add(situation);
        addAll(datasetId, situations);
        return Situations.situations.get(createKey(datasetId, situation));
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
