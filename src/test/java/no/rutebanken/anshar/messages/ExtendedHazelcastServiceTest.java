package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.messages.collections.ExtendedHazelcastService;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class ExtendedHazelcastServiceTest {

    @Autowired
    ExtendedHazelcastService extendedHazelcastService;

    @Test
    @Ignore
    public void testShutDownDiscovered() {
        assertTrue(extendedHazelcastService.isAlive());
        extendedHazelcastService.shutdown();
        assertFalse(extendedHazelcastService.isAlive());
    }


}
