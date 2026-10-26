package no.rutebanken.anshar.data;

import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.AbstractMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RemoveIfExpiredEntryProcessorTest {

    private ApplicationContext previousContext;

    @BeforeEach
    public void clearContext() {
        previousContext = ApplicationContextHolder.getContext();
        new ApplicationContextHolder().setApplicationContext(null);
    }

    @AfterEach
    public void restoreContext() {
        new ApplicationContextHolder().setApplicationContext(previousContext);
    }

    /**
     * A member that joins the cluster may be handed entries to process before its Spring context is set
     */
    @Test
    public void testEntryIsKeptWhileApplicationContextIsNotSet() {
        Map.Entry<SiriObjectStorageKey, String> entry =
                new AbstractMap.SimpleEntry<>(new SiriObjectStorageKey("TST", "1", "key"), "value");

        Boolean removed = new RemoveIfExpiredEntryProcessor<String>(SiriDataType.ESTIMATED_TIMETABLE, -1).process(entry);

        assertFalse(removed);
        assertEquals("value", entry.getValue());
    }
}
