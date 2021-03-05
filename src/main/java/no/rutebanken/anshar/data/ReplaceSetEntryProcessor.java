package no.rutebanken.anshar.data;

import com.hazelcast.map.EntryProcessor;

import java.util.Map;
import java.util.Set;

public class ReplaceSetEntryProcessor implements EntryProcessor<String, Set<SiriObjectStorageKey>, Set<SiriObjectStorageKey>> {

    private final Set<SiriObjectStorageKey> changes;

    public ReplaceSetEntryProcessor(Set<SiriObjectStorageKey> changes) {
        this.changes = changes;
    }

    @Override
    public Set<SiriObjectStorageKey> process(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        entry.setValue(changes);
        return changes;
    }

}
