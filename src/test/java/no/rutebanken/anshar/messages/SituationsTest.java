package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.junit.Test;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.SituationNumber;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertTrue;

public class SituationsTest {

    @Test
    public void testAddSituation() {
        int previousSize = Situations.getAll().size();
        PtSituationElement element = createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(4));

        Situations.add(element, "test");

        assertTrue(Situations.getAll().size() == previousSize+1);
    }
    @Test
    public void testAddNullSituation() {
        int previousSize = Situations.getAll().size();
        Situations.add(null, "test");

        assertTrue(Situations.getAll().size() == previousSize);
    }

    @Test
    public void testExpiredSituation() {
        int previousSize = Situations.getAll().size();
        PtSituationElement element = createPtSituationElement("kolumbus", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(1));

        Situations.add(element, "test");

        assertTrue(Situations.getAll().size() == previousSize); //No change
    }

    @Test
    public void testUpdatedSituation() {
        int previousSize = Situations.getAll().size();
        PtSituationElement element = createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add(element, "test");
        int expectedSize = previousSize+1;
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element2 = createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add(element2, "test");
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element3 = createPtSituationElement("kolumbus", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add(element3, "test");
        expectedSize++;
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element4 = createPtSituationElement("ruter", "1235", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add(element4, "test");

        expectedSize++;
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element5 = createPtSituationElement("ruter", "1235", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add(element4, "test2");

        expectedSize++;
        assertTrue(Situations.getAll().size() == expectedSize);

        assertTrue(Situations.getAll("test2").size() == previousSize+1);
        assertTrue(Situations.getAll("test").size() == expectedSize-1);
    }


    private PtSituationElement createPtSituationElement(String participantRef, String situationNumber, ZonedDateTime startTime, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        PtSituationElement.ValidityPeriod period = new PtSituationElement.ValidityPeriod();
        period.setStartTime(startTime);

        element.setParticipantRef(SiriObjectFactory.createRequestorRef(participantRef));

        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);


        //ValidityPeriod has already expired
        period.setEndTime(endTime);
        element.getValidityPeriods().add(period);
        return element;
    }
}
