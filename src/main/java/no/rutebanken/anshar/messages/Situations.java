package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.SituationExchangeRequestStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Situations extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(Situations.class);

    private static List<PtSituationElement> situations = getSituationsList();

    /**
     * @return All situations that are still valid
     */
    public static List<PtSituationElement> getAll() {
       removeExpiredElements();
        return situations;
    }

    private static void removeExpiredElements() {

        List<PtSituationElement> itemsToRemove = new ArrayList<>();

        for (int i = 0; i < situations.size(); i++) {
            PtSituationElement current = situations.get(i);
            if ( !isStillValid(current)) {
                itemsToRemove.add(current);
            }
        }
        situations.removeAll(itemsToRemove);
    }

    private static boolean isStillValid(PtSituationElement situationElement) {
        List<PtSituationElement.ValidityPeriod> validityPeriods = situationElement.getValidityPeriods();
        boolean isStillValid = false;

        if (validityPeriods != null) {
            for (PtSituationElement.ValidityPeriod validity : validityPeriods) {
                //Keep if at least one is valid
                if (validity.getEndTime() == null || validity.getEndTime().isAfter(ZonedDateTime.now())) {
                    isStillValid = true;
                }
            }
        } else {
            //No validity - keep "forever"
            isStillValid = true;
        }
        return isStillValid;
    }

    public static void add(PtSituationElement situation) {
        int indexToReplace = -1;
        for (int i = 0; i < situations.size(); i++) {
            PtSituationElement element = situations.get(i);
            if (element.getSituationNumber().getValue().equals(situation.getSituationNumber().getValue()) &&
                    element.getParticipantRef().getValue().equals(situation.getParticipantRef().getValue())) {

                //Same SituationNumber for same provider already exists - replace existing
                indexToReplace = i;
                break; //Found situation to replace - no need to continue
            }
        }
        if (indexToReplace >= 0) {
            situations.remove(indexToReplace);
            situations.add(indexToReplace, situation);
        } else {
            situations.add(situation);
        }
    }

    public static List<PtSituationElement> getAll(SituationExchangeRequestStructure requestStructure) {
        List<PtSituationElement> result = new ArrayList<>();

        getAll().forEach(s -> {
            List<LineRef> requestedLineReves = requestStructure.getLineReves();
           // s.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedLines().get(0).getLineReves().contains()

            result.add(s);
        });

        return result;
    }
}
