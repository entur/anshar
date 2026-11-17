package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.pubsub.publisher.SiriTopicPublisher;
import no.rutebanken.anshar.subscription.SiriDataType;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.PtSituationElement;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With direct publishing on, ServerSubscriptionManager leaves the ET and SX topics to SiriTopicPublisher and
 * only pushes to outbound subscriptions.
 */
public class ServerSubscriptionManagerTopicTest extends SpringBootBaseTest {

    @Autowired
    private ServerSubscriptionManager manager;

    private final ProducerTemplate etProducer = mock(ProducerTemplate.class);
    private final ProducerTemplate sxProducer = mock(ProducerTemplate.class);
    private final SiriTopicPublisher topicPublisher = mock(SiriTopicPublisher.class);
    private Object originalEtProducer, originalSxProducer, originalPublisher, originalEtEnabled, originalSxEnabled, originalExecutor;

    @BeforeEach
    void setUp() {
        originalEtProducer = ReflectionTestUtils.getField(manager, "siriEtTopicProducer");
        originalSxProducer = ReflectionTestUtils.getField(manager, "siriSxTopicProducer");
        originalPublisher = ReflectionTestUtils.getField(manager, "siriTopicPublisher");
        originalEtEnabled = ReflectionTestUtils.getField(manager, "pushToEtTopicEnabled");
        originalSxEnabled = ReflectionTestUtils.getField(manager, "pushToSxTopicEnabled");
        originalExecutor = ReflectionTestUtils.getField(manager, "outboundSenderExecutorService");
        // Pushes run on the calling thread, so they are done when pushUpdatesAsync returns
        ReflectionTestUtils.setField(manager, "outboundSenderExecutorService", MoreExecutors.newDirectExecutorService());
        ReflectionTestUtils.setField(manager, "siriEtTopicProducer", etProducer);
        ReflectionTestUtils.setField(manager, "siriSxTopicProducer", sxProducer);
        ReflectionTestUtils.setField(manager, "siriTopicPublisher", topicPublisher);
        ReflectionTestUtils.setField(manager, "pushToEtTopicEnabled", true);
        ReflectionTestUtils.setField(manager, "pushToSxTopicEnabled", true);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(manager, "siriEtTopicProducer", originalEtProducer);
        ReflectionTestUtils.setField(manager, "siriSxTopicProducer", originalSxProducer);
        ReflectionTestUtils.setField(manager, "siriTopicPublisher", originalPublisher);
        ReflectionTestUtils.setField(manager, "pushToEtTopicEnabled", originalEtEnabled);
        ReflectionTestUtils.setField(manager, "pushToSxTopicEnabled", originalSxEnabled);
        ReflectionTestUtils.setField(manager, "outboundSenderExecutorService", originalExecutor);
    }

    @Test
    void etTopicIsLeftToDirectPublisher() {
        when(topicPublisher.isEnabled()).thenReturn(true);

        manager.pushUpdatesAsync(SiriDataType.ESTIMATED_TIMETABLE, List.of(new EstimatedVehicleJourney()), "TST");

        verify(etProducer, never()).asyncRequestBodyAndHeaders((Endpoint) any(), any(), anyMap());
    }

    @Test
    void etTopicGoesThroughCamelWhenDirectPublishingIsOff() {
        when(topicPublisher.isEnabled()).thenReturn(false);

        manager.pushUpdatesAsync(SiriDataType.ESTIMATED_TIMETABLE, List.of(new EstimatedVehicleJourney()), "TST");

        verify(etProducer).asyncRequestBodyAndHeaders((Endpoint) any(), any(), anyMap());
    }

    @Test
    void sxTopicIsLeftToDirectPublisher() {
        when(topicPublisher.isEnabled()).thenReturn(true);

        manager.pushUpdatesAsync(SiriDataType.SITUATION_EXCHANGE, List.of(new PtSituationElement()), "TST");

        verify(sxProducer, never()).asyncRequestBodyAndHeaders((Endpoint) any(), any(), anyMap());
    }

    @Test
    void sxTopicGoesThroughCamelWhenDirectPublishingIsOff() {
        when(topicPublisher.isEnabled()).thenReturn(false);

        manager.pushUpdatesAsync(SiriDataType.SITUATION_EXCHANGE, List.of(new PtSituationElement()), "TST");

        verify(sxProducer).asyncRequestBodyAndHeaders((Endpoint) any(), any(), anyMap());
    }
}
