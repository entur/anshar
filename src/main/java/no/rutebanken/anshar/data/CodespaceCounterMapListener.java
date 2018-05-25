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

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;

class CodespaceCounterMapListener implements EntryAddedListener<String, String>, EntryEvictedListener<String, String> {


    private final IMap<String, Integer> counterMap;

    public CodespaceCounterMapListener(IMap<String, Integer> counterMap) {
        this.counterMap = counterMap;
    }

    @Override
    public void entryAdded( EntryEvent<String, String> event ) {
        updateCount(event.getKey().substring(0, 3), 1);
    }

    @Override
    public void entryEvicted( EntryEvent<String, String> event ) {
        updateCount(event.getKey().substring(0, 3), -1);
    }

    private void updateCount(String codespace, int diff) {
        counterMap.putIfAbsent(codespace, new Integer(0));
        int counter = counterMap.get(codespace);
        counterMap.set(codespace, counter + diff);
    }

}