package no.rutebanken.anshar.data;

import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;

import java.util.Map;
import java.util.Set;

public class AppendChangesToSetEntryProcessor implements EntryProcessor<String, Set<SiriObjectStorageKey>>,
                                                                EntryBackupProcessor<String, Set<SiriObjectStorageKey>> {

    private Set<SiriObjectStorageKey> changes;

    public AppendChangesToSetEntryProcessor(Set<SiriObjectStorageKey> changes) {
        this.changes = changes;
    }

    @Override
    public Object process(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        Set<SiriObjectStorageKey> value = entry.getValue();
        value.addAll(changes);
        entry.setValue(value);
        return null;
    }

    @Override
    public EntryBackupProcessor getBackupProcessor() {
        return AppendChangesToSetEntryProcessor.this;
    }

    @Override
    public void processBackup(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        Set<SiriObjectStorageKey> value = entry.getValue();
        value.addAll(changes);
        entry.setValue(value);
    }
}
