package no.rutebanken.anshar.data;

import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;

import java.util.Map;
import java.util.Set;

public class ReplaceSetEntryProcessor implements EntryProcessor<String, Set<String>>,
                                                                EntryBackupProcessor<String, Set<String>> {

    private final Set<String> changes;

    public ReplaceSetEntryProcessor(Set<String> changes) {
        this.changes = changes;
    }

    @Override
    public Object process(Map.Entry<String, Set<String>> entry) {
        entry.setValue(changes);
        return null;
    }

    @Override
    public EntryBackupProcessor getBackupProcessor() {
        return ReplaceSetEntryProcessor.this;
    }

    @Override
    public void processBackup(Map.Entry<String, Set<String>> entry) {
        entry.setValue(changes);
    }
}
