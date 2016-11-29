package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.SituationNumber;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SituationsTest {


    @Before
    public void setup() {
        Situations.situations.clear();
    }


    @Test
    public void testAddSituation() {
        int previousSize = Situations.getAll().size();
        PtSituationElement element = createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(4));

        Situations.add("test", element);

        assertTrue(Situations.getAll().size() == previousSize+1);
    }
    @Test
    public void testAddNullSituation() {
        int previousSize = Situations.getAll().size();
        Situations.add("test", null);

        assertTrue(Situations.getAll().size() == previousSize);
    }

    @Test
    public void testExpiredSituation() {
        int previousSize = Situations.getAll().size();
        PtSituationElement element = createPtSituationElement("kolumbus", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(1));

        Situations.add("test", element);

        Situations.situations.removeExpiredElements();
        assertTrue(Situations.getAll().size() == previousSize); //No change
    }

    @Test
    public void testUpdatedSituation() {
        int previousSize = Situations.getAll().size();
        PtSituationElement element = createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add("test", element);
        int expectedSize = previousSize+1;
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element2 = createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add("test", element2);
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element3 = createPtSituationElement("kolumbus", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add("test", element3);
        expectedSize++;
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element4 = createPtSituationElement("ruter", "1235", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add("test", element4);

        expectedSize++;
        assertTrue(Situations.getAll().size() == expectedSize);

        PtSituationElement element5 = createPtSituationElement("ruter", "1235", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        Situations.add("test2", element5);

        expectedSize++;
        assertTrue(Situations.getAll().size() == expectedSize);

        assertTrue(Situations.getAll("test2").size() == previousSize+1);
        assertTrue(Situations.getAll("test").size() == expectedSize-1);
    }

    @Test
    public void testGetUpdatesOnly() {

        assertEquals(0, Situations.getAll().size());

        Situations.add("test", createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        Situations.add("test", createPtSituationElement("ruter", "2345", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        Situations.add("test", createPtSituationElement("ruter", "3456", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        // Added 3
        assertEquals(3, Situations.getAllUpdates("1234-1234").size());

        Situations.add("test", createPtSituationElement("ruter", "4567", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));

        //Added one
        assertEquals(1, Situations.getAllUpdates("1234-1234").size());


        //None added
        assertEquals(0, Situations.getAllUpdates("1234-1234").size());

        //Verify that all elements still exist
        assertEquals(4, Situations.getAll().size());
    }


    private PtSituationElement createPtSituationElement(String participantRef, String situationNumber, ZonedDateTime startTime, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
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
