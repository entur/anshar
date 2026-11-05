/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.data;

import com.hazelcast.map.EntryProcessor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Removes keys from a requestor's change set in one atomic operation.
 * <p>
 * Returns the claimed keys that were still in the set, so keys taken by a concurrent request are not returned twice.
 * Changes appended to the set after the caller read it are kept.
 */
public class ClaimChangesEntryProcessor implements EntryProcessor<String, Set<SiriObjectStorageKey>, Set<SiriObjectStorageKey>> {

    private final Set<SiriObjectStorageKey> claimed;
    private final Set<SiriObjectStorageKey> discarded;

    /**
     * @param claimed   keys returned to the requestor
     * @param discarded keys that are removed without being returned
     */
    public ClaimChangesEntryProcessor(Set<SiriObjectStorageKey> claimed, Set<SiriObjectStorageKey> discarded) {
        this.claimed = claimed;
        this.discarded = discarded;
    }

    @Override
    public Set<SiriObjectStorageKey> process(Map.Entry<String, Set<SiriObjectStorageKey>> entry) {
        Set<SiriObjectStorageKey> value = entry.getValue();
        if (value == null) {
            return new HashSet<>();
        }

        Set<SiriObjectStorageKey> result = new HashSet<>(claimed);
        result.retainAll(value);

        value.removeAll(claimed);
        value.removeAll(discarded);
        entry.setValue(value);

        return result;
    }
}
