package no.rutebanken.anshar.data;

import com.hazelcast.config.SerializationConfig;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiriObjectStorageKeySerializerTest {

    /**
     * Same serializer configuration as the running application
     */
    private static SerializationService createSerializationService() {
        SerializationConfig config = new SerializationConfig();
        new ExtendedHazelcastService(null, 2).getSerializerConfigs().forEach(config::addSerializerConfig);
        return new DefaultSerializationServiceBuilder().setConfig(config).build();
    }

    private static SiriObjectStorageKey roundTrip(SerializationService serializationService, SiriObjectStorageKey key) {
        Data data = serializationService.toData(key);
        return serializationService.toObject(data);
    }

    @Test
    public void testUsesCustomSerializer() {
        SerializationService serializationService = createSerializationService();

        Data data = serializationService.toData(new SiriObjectStorageKey("RUT", "RUT:Line:1", "RUT:ServiceJourney:1"));

        assertEquals(SiriObjectStorageKeySerializer.TYPE_ID, data.getType());
    }

    @Test
    public void testRoundTrip() {
        SerializationService serializationService = createSerializationService();
        SiriObjectStorageKey key = new SiriObjectStorageKey("RUT", "RUT:Line:1", "RUT:ServiceJourney:1");

        SiriObjectStorageKey result = roundTrip(serializationService, key);

        assertEquals(key, result);
        assertEquals(key.hashCode(), result.hashCode());
        assertEquals("RUT", result.getCodespaceId());
        assertEquals("RUT:Line:1", result.getLineRef());
        assertEquals("RUT:ServiceJourney:1", result.getKey());
    }

    @Test
    public void testNullAndEmptyFieldsAreDistinct() {
        SerializationService serializationService = createSerializationService();
        SiriObjectStorageKey withNulls = new SiriObjectStorageKey(null, null, null);
        SiriObjectStorageKey withEmpty = new SiriObjectStorageKey("", "", "");

        SiriObjectStorageKey nullResult = roundTrip(serializationService, withNulls);
        SiriObjectStorageKey emptyResult = roundTrip(serializationService, withEmpty);

        assertNull(nullResult.getCodespaceId());
        assertNull(nullResult.getLineRef());
        assertNull(nullResult.getKey());
        assertEquals("", emptyResult.getCodespaceId());
        assertEquals("", emptyResult.getLineRef());
        assertEquals("", emptyResult.getKey());
        assertEquals(withNulls, nullResult);
        assertEquals(withEmpty, emptyResult);
        assertNotEquals(nullResult, emptyResult);
    }

    @Test
    public void testNonAsciiRoundTrip() {
        SerializationService serializationService = createSerializationService();
        SiriObjectStorageKey key = new SiriObjectStorageKey("SKY", "SKY:Line:Ærøy-Ålesund", "SKY:ServiceJourney:Bø-ø-€-😀");

        assertEquals(key, roundTrip(serializationService, key));
    }

    @Test
    public void testChangeSetRoundTrip() {
        SerializationService serializationService = createSerializationService();
        Set<SiriObjectStorageKey> changes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            changes.add(new SiriObjectStorageKey("RUT", "RUT:Line:" + (i % 50), "RUT:ServiceJourney:" + i));
        }
        changes.add(new SiriObjectStorageKey("RUT", null, "RUT:ServiceJourney:no-line"));

        Set<SiriObjectStorageKey> result = serializationService.toObject(serializationService.toData(changes));

        assertEquals(changes, result);
        assertTrue(result.contains(new SiriObjectStorageKey("RUT", "RUT:Line:7", "RUT:ServiceJourney:7")));
        assertTrue(result.contains(new SiriObjectStorageKey("RUT", null, "RUT:ServiceJourney:no-line")));
    }

    @Test
    public void testSerializedKeyIsMuchSmallerThanJavaSerialization() {
        SiriObjectStorageKey key = new SiriObjectStorageKey("RUT", "RUT:Line:1", "RUT:ServiceJourney:1");
        SerializationService javaSerialization = new DefaultSerializationServiceBuilder().build();

        int compactSize = createSerializationService().toData(key).totalSize();
        int javaSize = javaSerialization.toData(key).totalSize();

        assertTrue(compactSize * 2 < javaSize, "compact=" + compactSize + " bytes, java=" + javaSize + " bytes");
    }
}
