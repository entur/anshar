package no.rutebanken.anshar.data;

import com.google.common.base.Objects;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.StringJoiner;

public class SiriObjectStorageKey implements Serializable {

    static final String CODESPACE_ID_LABEL = "codespaceId";
    static final String LINE_REF_LABEL = "lineRef";
    static final String KEY_LABEL = "key";
    private String codespaceId;
    private String lineRef;

    private String key;


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