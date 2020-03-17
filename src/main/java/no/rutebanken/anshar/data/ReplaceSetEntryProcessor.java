package no.rutebanken.anshar.data;

import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;

import java.util.Map;
import java.util.Set;

public class ReplaceSetEntryProcessor implements EntryProcessor<String, Set<SiriObjectStorageKey>>,
                                                                EntryBackupProcessor<String, Set<SiriObjectStorageKey>> {

    private final Set<SiriObjectStorageKey> changes;

    public ReplaceSetEntryProcessor(Set<SiriObjectStorageKey> changes) {
        this.changes = changes;
    }

    @Override
    public Object process(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        entry.setValue(changes);
        return null;
    }

    @Override
    public EntryBackupProcessor getBackupProcessor() {
        return ReplaceSetEntryProcessor.this;
    }

    @Override
    public void processBackup(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        entry.setValue(changes);
    }
}
