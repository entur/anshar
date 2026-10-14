package no.rutebanken.anshar.routes.validation;

import com.hazelcast.core.ReadOnly;
import com.hazelcast.map.EntryProcessor;

import java.util.Map;

/**
 * Returns the size of the stored value without transferring the value itself
 */
public class ValueLengthEntryProcessor implements EntryProcessor<String, byte[], Integer>, ReadOnly {

    @Override
    public Integer process(Map.Entry<String, byte[]> entry) {
        byte[] value = entry.getValue();
        return value != null ? value.length : 0;
    }

    @Override
    public EntryProcessor<String, byte[], Integer> getBackupProcessor() {
        return null;
    }
}
