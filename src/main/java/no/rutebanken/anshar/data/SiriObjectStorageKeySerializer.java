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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;

/**
 * Compact Hazelcast-serialization of SiriObjectStorageKey - used for all map-keys and change-sets.
 * Replaces Java serialization, which wrote a full class-descriptor for every key.
 */
public class SiriObjectStorageKeySerializer implements StreamSerializer<SiriObjectStorageKey> {

    static final int TYPE_ID = 2;

    @Override
    public void write(ObjectDataOutput out, SiriObjectStorageKey key) throws IOException {
        out.writeString(key.getCodespaceId());
        out.writeString(key.getLineRef());
        out.writeString(key.getKey());
    }

    @Override
    public SiriObjectStorageKey read(ObjectDataInput in) throws IOException {
        String codespaceId = in.readString();
        String lineRef = in.readString();
        String key = in.readString();
        return new SiriObjectStorageKey(codespaceId, lineRef, key);
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }
}
