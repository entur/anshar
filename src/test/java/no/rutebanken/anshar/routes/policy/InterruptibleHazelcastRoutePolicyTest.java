package no.rutebanken.anshar.routes.policy;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that stopping singleton route policies does not hold up shutdown while other members own the locks.
 */
public class InterruptibleHazelcastRoutePolicyTest {

    private static final String LOCK_MAP = "test.route.locks";

    private HazelcastInstance hazelcast;
    private CamelContext camelContext;
    private IMap<String, String> locks;

    private final CountDownLatch releaseLocks = new CountDownLatch(1);
    private Thread lockHolder;

    @BeforeEach
    public void setUp() {
        Config config = new Config();
        config.setClusterName("anshar-test-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPortAutoIncrement(true);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        hazelcast = Hazelcast.newHazelcastInstance(config);
        locks = hazelcast.getMap(LOCK_MAP);

        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        releaseLocks.countDown();
        if (lockHolder != null) {
            lockHolder.join(5_000);
        }
        camelContext.stop();
        hazelcast.getLifecycleService().terminate();
    }

    /**
     * Hazelcast locks are owned by the thread that took them - a separate thread plays the role of the
     * leader on another member.
     */
    private void holdLocksInOtherThread(List<String> keys) throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        lockHolder = new Thread(() -> {
            keys.forEach(locks::lock);
            locked.countDown();
            try {
                releaseLocks.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                keys.forEach(locks::unlock);
            }
        });
        lockHolder.start();
        assertTrue(locked.await(10, TimeUnit.SECONDS), "locks taken by other thread");
    }

    private RecordingPolicy createPolicy(String key) throws Exception {
        RecordingPolicy policy = new RecordingPolicy(hazelcast);
        policy.setLockMapName(LOCK_MAP);
        policy.setLockKey(key);
        policy.setLockValue("lockValue");
        policy.setShouldStopConsumer(false);
        policy.setCamelContext(camelContext);
        policy.start();
        return policy;
    }

    @Test
    public void testStoppingPoliciesWaitingForLocksIsFast() throws Exception {
        List<String> keys = List.of("route-1", "route-2", "route-3");
        holdLocksInOtherThread(keys);

        List<RecordingPolicy> policies = new ArrayList<>();
        for (String key : keys) {
            policies.add(createPolicy(key));
        }
        awaitTryLockAttempts(3);

        long start = System.currentTimeMillis();
        for (RecordingPolicy policy : policies) {
            policy.stop();
        }
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 3_000, "Stopping 3 policies took " + elapsed + " ms");
        policies.forEach(policy -> assertFalse(policy.isLeader()));
    }

    @Test
    public void testLeaderReleasesLockOnStop() throws Exception {
        RecordingPolicy policy = createPolicy("route-leader");
        awaitTrue(policy::isLeader, "policy becomes leader");

        policy.stop();

        assertFalse(policy.isLeader());
        assertFalse(locks.isLocked("route-leader"), "lock released by stopped leader");
    }

    @Test
    public void testLockAcquiredAfterStopIsReleasedWithoutTakingLeadership() throws Exception {
        String key = "route-late";
        holdLocksInOtherThread(List.of(key));
        RecordingPolicy policy = createPolicy(key);
        awaitTryLockAttempts(1);

        policy.stop();
        // The other member leaves - the stopped policy's pending tryLock now succeeds
        releaseLocks.countDown();
        lockHolder.join(5_000);

        awaitTrue(() -> !locks.isLocked(key), "lock released by stopped policy");
        Thread.sleep(500);
        assertFalse(locks.isLocked(key), "lock not kept by stopped policy");
        assertFalse(policy.becameLeader.get(), "stopped policy must not take leadership");
    }

    @Test
    public void testLockAcquiredByPreviousRunIsReleasedAfterRestart() throws Exception {
        String key = "route-restart";
        holdLocksInOtherThread(List.of(key));
        RecordingPolicy policy = createPolicy(key);
        awaitTryLockAttempts(1);

        policy.stop();
        policy.start();
        // The attempt from the stopped run is still pending next to the new one
        awaitTryLockAttempts(2);
        releaseLocks.countDown();
        lockHolder.join(5_000);
        awaitTrue(policy::isLeader, "restarted policy becomes leader");
        assertEquals(1, policy.leadershipTaken.get(), "only the restarted run may take leadership");

        long start = System.currentTimeMillis();
        policy.stop();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 3_000, "Stopping restarted leader took " + elapsed + " ms");
        awaitTrue(() -> !locks.isLocked(key), "lock released after stopping restarted policy");
    }

    @Test
    public void testStoppingWhileLeadershipIsTakenDoesNotDeadlock() throws Exception {
        String key = "route-stop-while-taking-leadership";
        RecordingPolicy policy = new RecordingPolicy(hazelcast);
        policy.setLockMapName(LOCK_MAP);
        policy.setLockKey(key);
        policy.setLockValue("lockValue");
        policy.setShouldStopConsumer(false);
        policy.setCamelContext(camelContext);

        Thread stopper = new Thread(() -> {
            try {
                policy.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "policy-stopper");
        stopper.setDaemon(true);
        // The worker takes leadership while stop() is in progress - it continues once the stopper is blocked
        policy.beforeLeadership = () -> awaitBlocked(stopper);
        policy.start();
        assertTrue(policy.leadershipStarted.await(10, TimeUnit.SECONDS), "worker started taking leadership");

        stopper.start();
        stopper.join(10_000);

        assertFalse(stopper.isAlive(), "stop() deadlocked with the worker taking leadership");
        awaitTrue(() -> !locks.isLocked(key), "lock released after stopping policy");
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (thread.getState() != Thread.State.BLOCKED && thread.getState() != Thread.State.WAITING) {
            if (System.currentTimeMillis() > deadline) {
                return;
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Waits until the given number of policy threads are blocked in tryLock - the call that ignores interrupts
     */
    private static void awaitTryLockAttempts(int count) throws InterruptedException {
        awaitTrue(() -> Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().contains(RecordingPolicy.class.getSimpleName()))
                .filter(e -> Arrays.stream(e.getValue()).anyMatch(frame -> frame.getMethodName().equals("tryLock")))
                .count() == count, count + " pending tryLock attempts");
    }

    private static void awaitTrue(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for: " + description);
            }
            Thread.sleep(50);
        }
    }

    private static class RecordingPolicy extends InterruptibleHazelcastRoutePolicy {
        private final AtomicBoolean becameLeader = new AtomicBoolean();
        private final AtomicInteger leadershipTaken = new AtomicInteger();
        private final CountDownLatch leadershipStarted = new CountDownLatch(1);
        private volatile Runnable beforeLeadership = () -> {};

        RecordingPolicy(HazelcastInstance instance) {
            super(instance);
        }

        @Override
        protected void setLeader(boolean isLeader) {
            if (isLeader) {
                leadershipStarted.countDown();
                beforeLeadership.run();
                becameLeader.set(true);
                leadershipTaken.incrementAndGet();
            }
            super.setLeader(isLeader);
        }
    }
}
