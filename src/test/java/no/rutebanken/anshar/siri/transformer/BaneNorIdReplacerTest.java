package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.siri.transformer.impl.BaneNorIdReplacer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import static junit.framework.TestCase.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class BaneNorIdReplacerTest {

    @Test
    public void testSimpleMapping() {

        BaneNorIdReplacer mapper = new BaneNorIdReplacer(JourneyPlaceRefStructure.class);

        assertEquals("007600205", mapper.apply("FJE"));
    }

}
