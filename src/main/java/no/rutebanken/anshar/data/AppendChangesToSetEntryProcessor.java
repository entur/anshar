package no.rutebanken.anshar.data;

import com.hazelcast.map.EntryProcessor;

import java.util.Map;
import java.util.Set;

public class AppendChangesToSetEntryProcessor implements EntryProcessor<String, Set<SiriObjectStorageKey>, Set<SiriObjectStorageKey>> {

    private Set<SiriObjectStorageKey> changes;

    public AppendChangesToSetEntryProcessor(Set<SiriObjectStorageKey> changes) {
        this.changes = changes;
    }

    @Override
    public Set<SiriObjectStorageKey> process(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        Set<SiriObjectStorageKey> value = entry.getValue();
        value.addAll(changes);
        entry.setValue(value);
        return value;
    }

    //
//    @Override
//    public EntryBackupProcessor getBackupProcessor() {
//        return AppendChangesToSetEntryProcessor.this;
//    }
//
//    @Override
//    public void processBackup(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
//        Set<SiriObjectStorageKey> value = entry.getValue();
//        value.addAll(changes);
//        entry.setValue(value);
//    }
}
