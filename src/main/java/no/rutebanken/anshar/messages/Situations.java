package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.PtSituationElement;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Situations extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(Situations.class);

    private static Map<String,PtSituationElement> situations = getSituationsMap();

    /**
     * @return All situations that are still valid
     */
    public static List<PtSituationElement> getAll() {
       removeExpiredElements();
        return new ArrayList<>(situations.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<PtSituationElement> getAll(String datasetId) {
        removeExpiredElements();

        Map<String, PtSituationElement> datasetIdSpecific = new HashMap<>();
        situations.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            PtSituationElement element = situations.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }

    private static void removeExpiredElements() {

        List<String> itemsToRemove = new ArrayList<>();

        for (String key : situations.keySet()) {
            PtSituationElement current = situations.get(key);
            if ( !isStillValid(current)) {
                itemsToRemove.add(key);
            }
        }

        for (String rm : itemsToRemove) {
            situations.remove(rm);
        }
    }

    private static boolean isStillValid(PtSituationElement situationElement) {
        List<PtSituationElement.ValidityPeriod> validityPeriods = situationElement.getValidityPeriods();

        if (validityPeriods != null) {
            for (PtSituationElement.ValidityPeriod validity : validityPeriods) {
                //Keep if at least one is valid
                if (validity.getEndTime() == null || validity.getEndTime().isAfter(ZonedDateTime.now())) {
                    return true;
                }
            }
        } else {
            //No validity - keep "forever"
            return true;
        }
        return false;
    }

    public static void add(PtSituationElement situation, String datasetId) {
        if (situation == null) {return;}
        situations.put(createKey(datasetId, situation), situation);
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
