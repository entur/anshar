package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.SituationExchangeRequestStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Situations {
    private static Logger logger = LoggerFactory.getLogger(Situations.class);

    private static List<PtSituationElement> situations = new ArrayList<>();

    /**
     * @return All situations that are still valid
     */
    public static List<PtSituationElement> getAll() {
        situations.removeIf(s -> {
            List<PtSituationElement.ValidityPeriod> validityPeriods = s.getValidityPeriods();
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
            return !isStillValid;
        });

        return situations;
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
