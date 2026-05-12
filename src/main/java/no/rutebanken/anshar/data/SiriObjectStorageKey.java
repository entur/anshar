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

import com.google.common.base.Objects;

import java.io.Serializable;
import java.util.StringJoiner;

public class SiriObjectStorageKey implements Serializable {

    private final String codespaceId;
    private final String lineRef;

    private final String key;

    public SiriObjectStorageKey(String codespaceId, String lineRef, String key) {
        this.codespaceId = codespaceId;
        this.lineRef = lineRef;
        this.key = key;
    }

    String getCodespaceId() {
        return codespaceId;
    }

    String getLineRef() {
        return lineRef;
    }

    String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SiriObjectStorageKey.class.getSimpleName() + "[", "]")
                .add("codespaceId='" + codespaceId + "'")
                .add("lineRef='" + lineRef + "'")
                .add("key='" + key + "'")
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SiriObjectStorageKey that = (SiriObjectStorageKey) o;
        return Objects.equal(codespaceId, that.codespaceId) &&
                Objects.equal(lineRef, that.lineRef) &&
                Objects.equal(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codespaceId, lineRef, key);
    }
}