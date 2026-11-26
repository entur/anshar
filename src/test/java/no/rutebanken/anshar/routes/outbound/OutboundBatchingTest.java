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
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private Object originalCamelRouteManager, originalExecutor, originalBatcher, originalSxWindow, originalVmWindow, originalVmBatchMax, originalDisabledRequestors;

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
        originalVmWindow = ReflectionTestUtils.getField(manager, "batchWindowVm");
        originalVmBatchMax = ReflectionTestUtils.getField(manager, "batchMaxObjectsVm");
        originalDisabledRequestors = ReflectionTestUtils.getField(manager, "batchDisabledRequestors");
        // Pushes run on the calling thread, so they are done when pushUpdatesAsync returns
        ReflectionTestUtils.setField(manager, "outboundSenderExecutorService", MoreExecutors.newDirectExecutorService());
        ReflectionTestUtils.setField(manager, "camelRouteManager", camelRouteManager);
        ReflectionTestUtils.setField(manager, "batcher", new OutboundBatcher(scheduler, manager::flushBatch, manager::batchMaxObjects));
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
        ReflectionTestUtils.setField(manager, "batchWindowVm", originalVmWindow);
        ReflectionTestUtils.setField(manager, "batchMaxObjectsVm", originalVmBatchMax);
        ReflectionTestUtils.setField(manager, "batchDisabledRequestors", originalDisabledRequestors);
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
        OutboundSubscriptionSetup subscription = manager.subscriptions.get("batch-sx-property");
        // Only a requested window is stored - the configured one is read when pushing
        assertEquals(0, subscription.getUpdateInterval());
        assertEquals(1000, manager.batchWindow(subscription));
    }

    @Test
    void windowIsClampedToMax() throws Exception {
        subscribeVm("batch-vm-clamped", "http://localhost/vm-clamped", "PT5M");
        assertEquals(30000, manager.batchWindow(manager.subscriptions.get("batch-vm-clamped")));
    }

    @Test
    void changedConfiguredWindowAppliesToSubscriptionCreatedWithTheOldOne() throws Exception {
        ReflectionTestUtils.setField(manager, "batchWindowSx", Duration.ofSeconds(1));
        subscribeSx("batch-sx-changed", "http://localhost/sx-changed");

        ReflectionTestUtils.setField(manager, "batchWindowSx", Duration.ofSeconds(3));

        assertEquals(3000, manager.batchWindow(manager.subscriptions.get("batch-sx-changed")));
    }

    @Test
    void changedMaxAppliesToRequestedWindow() throws Exception {
        Object originalMax = ReflectionTestUtils.getField(manager, "batchWindowMax");
        try {
            subscribeVm("batch-vm-max", "http://localhost/vm-max", "PT10S");

            ReflectionTestUtils.setField(manager, "batchWindowMax", Duration.ofSeconds(5));

            assertEquals(5000, manager.batchWindow(manager.subscriptions.get("batch-vm-max")));
        } finally {
            ReflectionTestUtils.setField(manager, "batchWindowMax", originalMax);
        }
    }

    @Test
    void requestedZeroWindowIsNotBatched() throws Exception {
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofSeconds(2));
        subscribeVm("batch-requested-zero", "http://localhost/requested-zero", "PT0S");

        pushVm(1);

        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-requested-zero")), eq(false));
        assertTrue(scheduledTasks.isEmpty());
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
    void subscriptionWithoutWindowUsesTheConfiguredWindow() throws Exception {
        // Created while no window was configured - e.g. before batching was turned on
        subscribeVm("batch-existing", "http://localhost/existing", null);
        assertEquals(0, manager.subscriptions.get("batch-existing").getUpdateInterval());
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofSeconds(2));

        pushVm(1);
        pushVm(1);
        verify(camelRouteManager, never()).pushSiriData(any(Siri.class), argThat(subscription("batch-existing")), eq(false));
        verify(scheduler).schedule(any(Runnable.class), eq(2000L), eq(TimeUnit.MILLISECONDS));

        runTimers();
        ArgumentCaptor<Siri> delivery = ArgumentCaptor.forClass(Siri.class);
        verify(camelRouteManager, times(1)).pushSiriData(delivery.capture(), argThat(subscription("batch-existing")), eq(false));
        assertEquals(2, delivery.getValue().getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
    }

    @Test
    void configuredWindowForExistingSubscriptionIsClampedToMax() throws Exception {
        subscribeVm("batch-existing-clamped", "http://localhost/existing-clamped", null);
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofMinutes(5));

        pushVm(1);

        verify(scheduler).schedule(any(Runnable.class), eq(30000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void requestedWindowWinsOverTheConfiguredWindow() throws Exception {
        subscribeVm("batch-requested", "http://localhost/requested", "PT3S");
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofSeconds(1));

        pushVm(1);

        verify(scheduler).schedule(any(Runnable.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void statsShowTheWindowInUse() throws Exception {
        subscribeVm("batch-stats", "http://localhost/stats", null);
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofSeconds(2));

        String stats = manager.getSubscriptionsAsJson().toJSONString();

        assertTrue(stats.contains("\"updateInterval\":\"2.0 s\""), stats);
    }

    @Test
    void batchIsSentWhenTheMaxForItsTypeIsReached() throws Exception {
        ReflectionTestUtils.setField(manager, "batchMaxObjectsVm", 2);
        subscribeVm("batch-type-max", "http://localhost/type-max", "PT2S");

        pushVm(5);

        ArgumentCaptor<Siri> delivery = ArgumentCaptor.forClass(Siri.class);
        verify(camelRouteManager, times(2)).pushSiriData(delivery.capture(), argThat(subscription("batch-type-max")), eq(false));
        for (Siri siri : delivery.getAllValues()) {
            assertEquals(2, siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
        }

        runTimers();
        verify(camelRouteManager, times(3)).pushSiriData(delivery.capture(), argThat(subscription("batch-type-max")), eq(false));
        assertEquals(1, delivery.getValue().getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
    }

    @Test
    void noRequestorIsExcludedFromBatchingByDefault() {
        assertEquals(Set.of(), ReflectionTestUtils.getField(manager, "batchDisabledRequestors"));
    }

    @Test
    void listedRequestorGetsEveryUpdateAtOnce() throws Exception {
        // The test subscriptions have RequestorRef batch-test
        ReflectionTestUtils.setField(manager, "batchDisabledRequestors", Set.of("someone-else", "batch-test"));
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofSeconds(2));
        subscribeVm("batch-disabled", "http://localhost/disabled", "PT3S");

        pushVm(1);
        pushVm(1);

        assertEquals(0, manager.batchWindow(manager.subscriptions.get("batch-disabled")));
        verify(camelRouteManager, times(2)).pushSiriData(any(Siri.class), argThat(subscription("batch-disabled")), eq(false));
        assertTrue(scheduledTasks.isEmpty());
    }

    @Test
    void requestorNotListedIsStillBatched() throws Exception {
        ReflectionTestUtils.setField(manager, "batchDisabledRequestors", Set.of("someone-else"));
        ReflectionTestUtils.setField(manager, "batchWindowVm", Duration.ofSeconds(2));
        subscribeVm("batch-not-disabled", "http://localhost/not-disabled", null);

        assertEquals(2000, manager.batchWindow(manager.subscriptions.get("batch-not-disabled")));
    }

    @Test
    void estimatedTimetableBatchesAreSmallerByDefault() {
        // A journey is ~15 KB, so 1000 of them made 15 MB posts that receivers could not take
        OutboundSubscriptionSetup et = new OutboundSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE, "http://localhost/et", 100, new ArrayList<>(), "batch-et-max");
        OutboundSubscriptionSetup vm = new OutboundSubscriptionSetup(SiriDataType.VEHICLE_MONITORING, "http://localhost/vm", 100, new ArrayList<>(), "batch-vm-max");

        assertEquals(100, manager.batchMaxObjects(et));
        assertEquals(1000, manager.batchMaxObjects(vm));
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
        verify(camelRouteManager, never()).pushSiriData(any(), any(), anyBoolean());
        verify(camelRouteManager, never()).pushSiriData(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void terminatedThenResubscribedGetsNoStaleBuffer() throws Exception {
        subscribeVm("batch-stale-a", "http://localhost/stale", "PT2S");
        pushVm(1);
        manager.terminateSubscription("batch-stale-a", false);

        subscribeVm("batch-stale-b", "http://localhost/stale", "PT2S");

        runTimers();
        verify(camelRouteManager, never()).pushSiriData(any(), any(), anyBoolean());
        verify(camelRouteManager, never()).pushSiriData(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void addAfterSchedulerShutdownPushesImmediately() throws Exception {
        subscribeVm("batch-shutdown-a", "http://localhost/shutdown-a", "PT2S");
        subscribeVm("batch-shutdown-b", "http://localhost/shutdown-b", "PT2S");
        ScheduledExecutorService dead = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        dead.shutdown();
        ReflectionTestUtils.setField(manager, "batcher", new OutboundBatcher(dead, manager::flushBatch, 1000));

        pushVm(1);

        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-shutdown-a")), eq(false));
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-shutdown-b")), eq(false));
    }

    @Test
    void unexpectedBatcherFailurePushesImmediately() throws Exception {
        subscribeVm("batch-failure-a", "http://localhost/failure-a", "PT2S");
        subscribeVm("batch-failure-b", "http://localhost/failure-b", "PT2S");
        ScheduledExecutorService failing = mock(ScheduledExecutorService.class);
        when(failing.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenThrow(new IllegalStateException("test"));
        ReflectionTestUtils.setField(manager, "batcher", new OutboundBatcher(failing, manager::flushBatch, 1000));

        pushVm(1);

        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-failure-a")), eq(false));
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("batch-failure-b")), eq(false));
    }

    @Test
    void failedFallbackPushDoesNotStopOtherRecipients() throws Exception {
        subscribeVm("fallback-failure-a", "http://localhost/fallback-failure-a", "PT2S");
        subscribeVm("fallback-failure-b", "http://localhost/fallback-failure-b", "PT2S");
        ScheduledExecutorService failing = mock(ScheduledExecutorService.class);
        when(failing.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenThrow(new IllegalStateException("test"));
        ReflectionTestUtils.setField(manager, "batcher", new OutboundBatcher(failing, manager::flushBatch, 1000));
        doThrow(new IllegalStateException("push failed")).when(camelRouteManager)
                .pushSiriData(any(Siri.class), argThat(subscription("fallback-failure-a")), eq(false));

        assertDoesNotThrow(() -> pushVm(1));

        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("fallback-failure-a")), eq(false));
        verify(camelRouteManager, times(1)).pushSiriData(any(Siri.class), argThat(subscription("fallback-failure-b")), eq(false));
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
    void differentWindowReplaces() throws Exception {
        subscribeVm("batch-window-a", "http://localhost/different-window", "PT2S");
        subscribeVm("batch-window-b", "http://localhost/different-window", "PT5S");

        assertNull(manager.subscriptions.get("batch-window-a"));
        assertNotNull(manager.subscriptions.get("batch-window-b"));
        assertEquals(5000, manager.subscriptions.get("batch-window-b").getUpdateInterval());
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
