package no.rutebanken.anshar.data;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies SiriObjectStorageKey-serialization between cluster members and through backup-replication -
 * paths that a single embedded member never exercises.
 */
public class SiriObjectStorageKeyClusterTest {

    private static final int KEY_COUNT = 500;

    private HazelcastInstance member1;
    private HazelcastInstance member2;

    @AfterEach
    public void shutdown() {
        if (member1 != null) {
            member1.getLifecycleService().terminate();
        }
        if (member2 != null) {
            member2.getLifecycleService().terminate();
        }
    }

    /**
     * Two members on localhost, isolated from any other cluster in the same JVM, with one synchronous backup
     */
    private static Config createConfig(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        new ExtendedHazelcastService(null, 1).getSerializerConfigs()
                .forEach(config.getSerializationConfig()::addSerializerConfig);
        config.getMapConfig("default").setBackupCount(1);

        config.getNetworkConfig().setPortAutoIncrement(true);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        return config;
    }

    private static SiriObjectStorageKey createKey(int i) {
        return new SiriObjectStorageKey("TST", (i % 3 == 0) ? null : "TST:Line:" + (i % 20), "TST:ServiceJourney:" + i);
    }

    @Test
    public void testKeysAndChangeSetsAcrossMembersAndBackups() {
        String clusterName = "anshar-test-" + UUID.randomUUID();
        member1 = Hazelcast.newHazelcastInstance(createConfig(clusterName));
        member2 = Hazelcast.newHazelcastInstance(createConfig(clusterName));
        awaitTrue(() -> member2.getCluster().getMembers().size() == 2, "cluster of 2 members");

        // Both running members use the compact serializer for keys
        for (HazelcastInstance member : new HazelcastInstance[]{member1, member2}) {
            int typeId = ((SerializationServiceSupport) member).getSerializationService().toData(createKey(1)).getType();
            assertEquals(SiriObjectStorageKeySerializer.TYPE_ID, typeId);
        }

        // Keyed entries written from member 1 - spread over partitions owned by both members
        IMap<SiriObjectStorageKey, String> data1 = member1.getMap("anshar.test.data");
        for (int i = 0; i < KEY_COUNT; i++) {
            data1.set(createKey(i), "value-" + i);
        }

        // Change sets written from member 1, appended to by an entry processor started on member 2
        IMap<String, Set<SiriObjectStorageKey>> changes1 = member1.getMap("anshar.test.changes");
        Set<SiriObjectStorageKey> initialChanges = new HashSet<>();
        for (int i = 0; i < KEY_COUNT; i++) {
            initialChanges.add(createKey(i));
        }
        for (int requestor = 0; requestor < 10; requestor++) {
            changes1.set("requestor-" + requestor, new HashSet<>(initialChanges));
        }
        Set<SiriObjectStorageKey> appended = Set.of(createKey(KEY_COUNT), createKey(KEY_COUNT + 1), createKey(KEY_COUNT + 3));
        IMap<String, Set<SiriObjectStorageKey>> changes2 = member2.getMap("anshar.test.changes");
        changes2.executeOnEntries(new AppendChangesToSetEntryProcessor(appended));
        awaitTrue(() -> member2.getPartitionService().isClusterSafe(), "backups replicated");

        // Everything is readable from member 2
        IMap<SiriObjectStorageKey, String> data2 = member2.getMap("anshar.test.data");
        assertAllPresent(data2, changes2, appended);

        // Member 1 is gone - member 2 must serve every entry, including the appended keys, from backups
        member1.getLifecycleService().terminate();
        member1 = null;
        awaitTrue(() -> member2.getCluster().getMembers().size() == 1, "member 1 removed");
        awaitTrue(() -> member2.getPartitionService().isClusterSafe(), "backups promoted");
        assertAllPresent(data2, changes2, appended);
    }

    private static void awaitTrue(BooleanSupplier condition, String description) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for: " + description);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for: " + description);
            }
        }
    }

    private static void assertAllPresent(IMap<SiriObjectStorageKey, String> data,
                                         IMap<String, Set<SiriObjectStorageKey>> changes,
                                         Set<SiriObjectStorageKey> appended) {
        assertEquals(KEY_COUNT, data.size());
        for (int i = 0; i < KEY_COUNT; i++) {
            assertEquals("value-" + i, data.get(createKey(i)), "Missing data for " + createKey(i));
        }

        Map<String, Set<SiriObjectStorageKey>> allChanges = changes.getAll(changes.keySet());
        assertEquals(10, allChanges.size());
        for (Map.Entry<String, Set<SiriObjectStorageKey>> entry : allChanges.entrySet()) {
            Set<SiriObjectStorageKey> set = entry.getValue();
            assertEquals(KEY_COUNT + appended.size(), set.size(), entry.getKey());
            assertTrue(set.containsAll(appended), entry.getKey());
            assertTrue(set.contains(createKey(0)), entry.getKey());
        }
    }
}
