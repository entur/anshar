package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.collections.ExpiringConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.PtSituationElement;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.anshar.messages.collections.DistributedCollection.getSituationsMap;

public class Situations {
    private static Logger logger = LoggerFactory.getLogger(Situations.class);

    static ExpiringConcurrentMap<String,PtSituationElement> situations = getSituationsMap();
    static ExpiringConcurrentMap<String, Set<String>> changesMap = new ExpiringConcurrentMap<>(new ConcurrentHashMap<>(), 30, 300);

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

            Set<String> idSet = changesMap.remove(requestorId);
            changesMap.put(requestorId, new HashSet<>());
            if (idSet != null) {
                List<PtSituationElement> changes = new ArrayList<>();

                idSet.stream().forEach(key -> {
                    PtSituationElement element = situations.get(key);
                    if (element != null) {
                        changes.add(element);
                    }
                });
                return changes;
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

    public static PtSituationElement add(PtSituationElement situation, String datasetId) {
        if (situation == null) {
            return situation;
        }
        String key = createKey(datasetId, situation);

        changesMap.keySet().forEach(requestor -> {
            Set<String> ids = changesMap.get(requestor);
            ids.add(key);
            changesMap.put(requestor, ids);
        });

        PtSituationElement previousElement = situations.put(key, situation, getExpiration(situation));
        if (previousElement != null) {
            //Situation existed, and may have been updated
            /*
             * TODO: How to determine updated situation?
             */
            if (situation.getCreationTime().isAfter(previousElement.getCreationTime())) {
                return situation;
            }
        } else {
            return situation;
        }

        return null;
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
