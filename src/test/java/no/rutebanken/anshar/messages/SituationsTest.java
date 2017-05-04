package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.SituationNumber;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class SituationsTest {

    @Autowired
    Situations situations;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Before
    public void setup() {

    }


    @Test
    public void testAddSituation() {
        int previousSize = situations.getAll().size();
        PtSituationElement element = createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(4));

        situations.add("test", element);

        assertEquals("Situation not added", previousSize + 1, situations.getAll().size());
    }
    @Test
    public void testAddNullSituation() {
        int previousSize = situations.getAll().size();
        situations.add("test", null);

        assertEquals("Null-situation added", previousSize, situations.getAll().size());
    }

    @Test
    public void testUpdatedSituation() {
        int previousSize = situations.getAll().size();

        PtSituationElement element = createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        situations.add("test", element);
        int expectedSize = previousSize+1;
        assertEquals(expectedSize, situations.getAll().size());

        PtSituationElement element2 = createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        situations.add("test", element2);
        assertEquals(expectedSize, situations.getAll().size());

        PtSituationElement element3 = createPtSituationElement("kolumbus", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        situations.add("test", element3);
        expectedSize++;
        assertEquals(expectedSize, situations.getAll().size());

        PtSituationElement element4 = createPtSituationElement("ruter", "1235", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        situations.add("test", element4);

        expectedSize++;
        assertEquals(expectedSize, situations.getAll().size());

        PtSituationElement element5 = createPtSituationElement("ruter", "1235", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        situations.add("test2", element5);

        expectedSize++;
        assertEquals(expectedSize, situations.getAll().size());

        assertTrue(situations.getAll("test2").size() == previousSize+1);
        assertTrue(situations.getAll("test").size() == expectedSize-1);
    }

    @Test
    public void testGetUpdatesOnly() {

        int previousSize = situations.getAll().size();

        String prefix = "updates-";
        situations.add("test", createPtSituationElement("ruter", prefix+"1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        situations.add("test", createPtSituationElement("ruter", prefix+"2345", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        situations.add("test", createPtSituationElement("ruter", prefix+"3456", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        // Added 3
        assertEquals(previousSize+3, situations.getAllUpdates("1234-1234", null).size());

        situations.add("test", createPtSituationElement("ruter", prefix+"4567", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));

        //Added one
        assertEquals(1, situations.getAllUpdates("1234-1234", null).size());


        //None added
        assertEquals(0, situations.getAllUpdates("1234-1234", null).size());

        //Verify that all elements still exist
        assertEquals(previousSize+4, situations.getAll().size());
    }


    private PtSituationElement createPtSituationElement(String participantRef, String situationNumber, ZonedDateTime startTime, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(startTime);

        element.setParticipantRef(siriObjectFactory.createRequestorRef(participantRef));

        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);

        //ValidityPeriod has already expired
        period.setEndTime(endTime);
        element.getValidityPeriods().add(period);
        return element;
    }
}
