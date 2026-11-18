package no.rutebanken.anshar.siri.handler;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.pubsub.publisher.SiriTopicPublisher;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleRef;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Stored ET and SX elements are handed to the direct topic publisher with the codespace they are pushed to
 * outbound subscriptions under. The publisher decides itself whether it is switched on.
 */
public class SiriHandlerTopicPublishTest extends SpringBootBaseTest {

    private final SiriTopicPublisher topicPublisher = mock(SiriTopicPublisher.class);
    private Object originalPublisher;

    @Autowired
    private SubscriptionManager manager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SiriObjectFactory factory;

    // Swapped in, not @MockitoBean: a second Spring context breaks the metric tests that share the first
    @BeforeEach
    void useMockPublisher() {
        originalPublisher = ReflectionTestUtils.getField(handler, "siriTopicPublisher");
        ReflectionTestUtils.setField(handler, "siriTopicPublisher", topicPublisher);
    }

    @AfterEach
    void restorePublisher() {
        ReflectionTestUtils.setField(handler, "siriTopicPublisher", originalPublisher);
    }

    private SubscriptionSetup subscription(SiriDataType type, boolean useProvidedCodespaceId) {
        String datasetId = "DS" + UUID.randomUUID().toString().substring(0, 8);
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setSubscriptionId(datasetId + ":" + type);
        subscription.setDatasetId(datasetId);
        subscription.setSubscriptionType(type);
        subscription.setUseProvidedCodespaceId(useProvidedCodespaceId);
        manager.addSubscription(subscription.getSubscriptionId(), subscription);
        return subscription;
    }

    private static EstimatedVehicleJourney journey(String dataSource) {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setDataSource(dataSource);
        LineRef lineRef = new LineRef();
        lineRef.setValue(dataSource + ":Line:1");
        journey.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(dataSource + ":Vehicle:" + UUID.randomUUID());
        journey.setVehicleRef(vehicleRef);
        journey.setRecordedAtTime(ZonedDateTime.now());
        EstimatedCall call = new EstimatedCall();
        StopPointRefStructure stop = new StopPointRefStructure();
        stop.setValue("NSR:Quay:1");
        call.setStopPointRef(stop);
        call.setAimedArrivalTime(ZonedDateTime.now().plusHours(1));
        call.setOrder(BigInteger.ONE);
        EstimatedVehicleJourney.EstimatedCalls calls = new EstimatedVehicleJourney.EstimatedCalls();
        calls.getEstimatedCalls().add(call);
        journey.setEstimatedCalls(calls);
        return journey;
    }

    private static PtSituationElement situation(String participantRef) {
        PtSituationElement situation = new PtSituationElement();
        situation.setCreationTime(ZonedDateTime.now());
        situation.setParticipantRef(SiriObjectFactory.createRequestorRef(participantRef));
        SituationNumber number = new SituationNumber();
        number.setValue(participantRef + ":SituationNumber:" + UUID.randomUUID());
        situation.setSituationNumber(number);
        HalfOpenTimestampOutputRangeStructure validity = new HalfOpenTimestampOutputRangeStructure();
        validity.setStartTime(ZonedDateTime.now().minusMinutes(5));
        validity.setEndTime(ZonedDateTime.now().plusHours(1));
        situation.getValidityPeriods().add(validity);
        return situation;
    }

    private void handle(SubscriptionSetup subscription, Object siri) throws Exception {
        String xml = SiriXml.toXml((uk.org.siri.siri21.Siri) siri);
        handler.handleIncomingSiri(subscription.getSubscriptionId(), new ByteArrayInputStream(xml.getBytes()));
    }

    @Test
    void storedJourneysAreHandedOverPerProvidedCodespace() throws Exception {
        SubscriptionSetup subscription = subscription(SiriDataType.ESTIMATED_TIMETABLE, true);
        try {
            handle(subscription, factory.createETServiceDelivery(List.of(journey("RUT"), journey("RUT"))));

            ArgumentCaptor<List<?>> elements = ArgumentCaptor.forClass(List.class);
            verify(topicPublisher).publishAsync(eq(SiriDataType.ESTIMATED_TIMETABLE), elements.capture(), eq("RUT"));
            assertEquals(2, elements.getValue().size());
        } finally {
            manager.removeSubscription(subscription.getSubscriptionId());
        }
    }

    @Test
    void storedJourneysAreHandedOverUnderTheDatasetWithoutProvidedCodespace() throws Exception {
        SubscriptionSetup subscription = subscription(SiriDataType.ESTIMATED_TIMETABLE, false);
        try {
            handle(subscription, factory.createETServiceDelivery(List.of(journey(subscriptionCodespace(subscription)))));

            verify(topicPublisher).publishAsync(eq(SiriDataType.ESTIMATED_TIMETABLE), anyList(), eq(subscription.getDatasetId()));
        } finally {
            manager.removeSubscription(subscription.getSubscriptionId());
        }
    }

    private static String subscriptionCodespace(SubscriptionSetup subscription) {
        return subscription.getDatasetId();
    }

    @Test
    void storedSituationsAreHandedOver() throws Exception {
        SubscriptionSetup subscription = subscription(SiriDataType.SITUATION_EXCHANGE, true);
        try {
            handle(subscription, factory.createSXServiceDelivery(List.of(situation("ENT"))));

            verify(topicPublisher).publishAsync(eq(SiriDataType.SITUATION_EXCHANGE), anyList(), eq("ENT"));
        } finally {
            manager.removeSubscription(subscription.getSubscriptionId());
        }
    }

    @Test
    void publisherIsNeverAskedToBlock() throws Exception {
        // publishAsync is the only call: SiriHandler never waits on the publisher
        SubscriptionSetup subscription = subscription(SiriDataType.ESTIMATED_TIMETABLE, true);
        try {
            handle(subscription, factory.createETServiceDelivery(List.of(journey("RUT"))));

            verify(topicPublisher).publishAsync(any(), anyList(), any());
            org.mockito.Mockito.verifyNoMoreInteractions(topicPublisher);
        } finally {
            manager.removeSubscription(subscription.getSubscriptionId());
        }
    }
}
