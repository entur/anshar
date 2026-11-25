package no.rutebanken.anshar.routes.outbound;

import com.google.common.util.concurrent.MoreExecutors;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleActivityStructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbound pushes to a subscription with an update interval are collected and pushed once per window
 */
public class OutboundBatchingTest extends SpringBootBaseTest {

    private static final String DATASET = "BATCHTEST";

    @Autowired
    private ServerSubscriptionManager manager;

    private final CamelRouteManager camelRouteManager = mock(CamelRouteManager.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<String> createdIds = new ArrayList<>();
    private Object originalCamelRouteManager, originalExecutor, originalBatcher, originalSxWindow;

    @BeforeEach
    void setUp() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(inv -> {
            scheduledTasks.add(inv.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        originalCamelRouteManager = ReflectionTestUtils.getField(manager, "camelRouteManager");
        originalExecutor = ReflectionTestUtils.getField(manager, "outboundSenderExecutorService");
        originalBatcher = ReflectionTestUtils.getField(manager, "batcher");
        originalSxWindow = ReflectionTestUtils.getField(manager, "batchWindowSx");
        // Pushes run on the calling thread, so they are done when pushUpdatesAsync returns
        ReflectionTestUtils.setField(manager, "outboundSenderExecutorService", MoreExecutors.newDirectExecutorService());
        ReflectionTestUtils.setField(manager, "camelRouteManager", camelRouteManager);
        ReflectionTestUtils.setField(manager, "batcher", new OutboundBatcher(scheduler, manager::flushBatch, 1000));
    }

    @AfterEach
    void tearDown() {
        for (String id : createdIds) {
            manager.terminateSubscription(id, false);
        }
        ReflectionTestUtils.setField(manager, "camelRouteManager", originalCamelRouteManager);
        ReflectionTestUtils.setField(manager, "outboundSenderExecutorService", originalExecutor);
        ReflectionTestUtils.setField(manager, "batcher", originalBatcher);
        ReflectionTestUtils.setField(manager, "batchWindowSx", originalSxWindow);
    }

    @Test
    void vmStandardUpdateIntervalIsUsed() throws Exception {
        subscribeVm("batch-vm-standard", "http://localhost/vm-standard", "PT3S");
        assertEquals(3000, manager.subscriptions.get("batch-vm-standard").getUpdateInterval());
    }

    @Test
    void propertyDefaultIsUsedWithoutRequestValue() throws Exception {
        ReflectionTestUtils.setField(manager, "batchWindowSx", Duration.ofSeconds(1));
        subscribeSx("batch-sx-property", "http://localhost/sx-property");
        assertEquals(1000, manager.subscriptions.get("batch-sx-property").getUpdateInterval());
    }

    @Test
    void windowIsClampedToMax() throws Exception {
        subscribeVm("batch-vm-clamped", "http://localhost/vm-clamped", "PT5M");
        assertEquals(30000, manager.subscriptions.get("batch-vm-clamped").getUpdateInterval());
    }

    @Test
    void noWindowMeansZero() throws Exception {
        subscribeSx("batch-sx-zero", "http://localhost/sx-zero");
        assertEquals(0, manager.subscriptions.get("batch-sx-zero").getUpdateInterval());
    }

    @Test
    void zeroWindowPushesImmediatelyAsToday() throws Exception {
        subscribeVm("batch-zero", "http://localhost/zero", null);

        pushVm(1);
        pushVm(1);

        verify(camelRouteManager, times(2)).pushSiriData(any(Siri.class), argThat(subscription("batch-zero")), eq(false));
        assertTrue(scheduledTasks.isEmpty());
    }

    @Test
    void batchedSubscriptionGetsOnePushPerWindow() throws Exception {
        subscribeVm("batch-window", "http://localhost/window", "PT2S");

        pushVm(1);
        pushVm(1);
        pushVm(1);
        verify(camelRouteManager, never()).pushSiriData(any(Siri.class), argThat(subscription("batch-window")), eq(false));

        runTimers();

        ArgumentCaptor<Siri> delivery = ArgumentCaptor.forClass(Siri.class);
        verify(camelRouteManager, times(1)).pushSiriData(delivery.capture(), argThat(subscription("batch-window")), eq(false));
        assertEquals(3, delivery.getValue().getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
    }

    @Test
    void mixedRecipientsEachGetTheirMode() throws Exception {
        subscribeVm("batch-mixed-zero", "http://localhost/mixed-zero", null);
        subscribeVm("batch-mixed-window", "http://localhost/mixed-window", "PT2S");

        pushVm(1);
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-mixed-zero")), eq(false));
        verify(camelRouteManager, never()).pushSiriData(any(Siri.class), argThat(subscription("batch-mixed-window")), eq(false));

        runTimers();
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-mixed-zero")), eq(false));
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-mixed-window")), eq(false));
    }

    @Test
    void flushGoesToReplacingSubscription() throws Exception {
        subscribeVm("batch-replaced", "http://localhost/replaced", "PT2S");
        pushVm(1);

        subscribeVm("batch-replacing", "http://localhost/replaced", "PT2S");
        assertTrue(!manager.subscriptions.containsKey("batch-replaced"));

        runTimers();
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-replacing")), eq(false));
        verify(camelRouteManager, never()).pushSiriData(any(Siri.class), argThat(subscription("batch-replaced")), eq(false));
    }

    @Test
    void flushIsDroppedForTerminatedSubscription() throws Exception {
        subscribeVm("batch-terminated", "http://localhost/terminated", "PT2S");
        pushVm(1);

        manager.terminateSubscription("batch-terminated", false);

        runTimers();
        verify(camelRouteManager, never()).pushSiriData(any(Siri.class), argThat(subscription("batch-terminated")), eq(false));
    }

    @Test
    void initialDeliveryClearsBuffer() throws Exception {
        subscribeVm("batch-initial", "http://localhost/initial", "PT2S");
        pushVm(1);

        // Same id - nothing is replaced, so the subscription gets a new initial delivery
        subscribeVm("batch-initial", "http://localhost/initial", "PT2S");

        runTimers();
        verify(camelRouteManager, never()).pushSiriData(any(Siri.class), argThat(subscription("batch-initial")), eq(false));
    }

    @Test
    void differentWindowDoesNotReplace() throws Exception {
        subscribeVm("batch-window-a", "http://localhost/different-window", "PT2S");
        subscribeVm("batch-window-b", "http://localhost/different-window", "PT5S");

        assertNotNull(manager.subscriptions.get("batch-window-a"));
        assertNotNull(manager.subscriptions.get("batch-window-b"));
    }

    private static org.mockito.ArgumentMatcher<OutboundSubscriptionSetup> subscription(String id) {
        return s -> s != null && id.equals(s.getSubscriptionId());
    }

    private void runTimers() {
        List<Runnable> tasks = new ArrayList<>(scheduledTasks);
        scheduledTasks.clear();
        tasks.forEach(Runnable::run);
    }

    private void pushVm(int count) {
        List<VehicleActivityStructure> activities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            activities.add(new VehicleActivityStructure());
        }
        manager.pushUpdatesAsync(SiriDataType.VEHICLE_MONITORING, activities, DATASET);
    }

    private void subscribeVm(String id, String address, String updateInterval) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "  <SubscriptionRequest>\n" +
                "    <RequestTimestamp>2026-11-25T10:00:00Z</RequestTimestamp>\n" +
                "    <ConsumerAddress>" + address + "</ConsumerAddress>\n" +
                "    <RequestorRef>batch-test</RequestorRef>\n" +
                "    <SubscriptionContext>\n" +
                "      <HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "    </SubscriptionContext>\n" +
                "    <VehicleMonitoringSubscriptionRequest>\n" +
                "      <SubscriptionIdentifier>" + id + "</SubscriptionIdentifier>\n" +
                "      <InitialTerminationTime>2119-12-06T14:38:42Z</InitialTerminationTime>\n" +
                "      <VehicleMonitoringRequest version=\"2.0\">\n" +
                "        <RequestTimestamp>2026-11-25T10:00:00Z</RequestTimestamp>\n" +
                "      </VehicleMonitoringRequest>\n" +
                (updateInterval != null ? "      <UpdateInterval>" + updateInterval + "</UpdateInterval>\n" : "") +
                "    </VehicleMonitoringSubscriptionRequest>\n" +
                "  </SubscriptionRequest>\n" +
                "</Siri>";
        subscribe(id, xml);
    }

    private void subscribeSx(String id, String address) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "  <SubscriptionRequest>\n" +
                "    <RequestTimestamp>2026-11-25T10:00:00Z</RequestTimestamp>\n" +
                "    <ConsumerAddress>" + address + "</ConsumerAddress>\n" +
                "    <RequestorRef>batch-test</RequestorRef>\n" +
                "    <SubscriptionContext>\n" +
                "      <HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "    </SubscriptionContext>\n" +
                "    <SituationExchangeSubscriptionRequest>\n" +
                "      <SubscriptionIdentifier>" + id + "</SubscriptionIdentifier>\n" +
                "      <InitialTerminationTime>2119-12-06T14:38:42Z</InitialTerminationTime>\n" +
                "      <SituationExchangeRequest>\n" +
                "        <RequestTimestamp>2026-11-25T10:00:00Z</RequestTimestamp>\n" +
                "      </SituationExchangeRequest>\n" +
                "    </SituationExchangeSubscriptionRequest>\n" +
                "  </SubscriptionRequest>\n" +
                "</Siri>";
        subscribe(id, xml);
    }

    private void subscribe(String id, String xml) throws Exception {
        createdIds.add(id);
        Siri response = manager.handleSubscriptionRequest(SiriXml.parseXml(xml), DATASET, OutboundIdMappingPolicy.DEFAULT, null);
        assertTrue(response.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());
    }
}
