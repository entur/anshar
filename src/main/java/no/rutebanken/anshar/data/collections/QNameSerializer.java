package no.rutebanken.anshar.data.collections;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import javax.xml.namespace.QName;

/**
 * Serializer for QName objects.
 * Ref.: https://github.com/EsotericSoftware/kryo/issues/885
 */
public class QNameSerializer extends Serializer<QName> {
    @Override
    public void write(Kryo kryo, Output output, QName qName) {
        output.writeString(qName.getNamespaceURI());
        output.writeString(qName.getLocalPart());
        output.writeString(qName.getPrefix());
    }

    @Override
    public QName read(Kryo kryo, Input input, Class<QName> type) {
        String namespaceURI = input.readString();
        String localPart = input.readString();
        String prefix = input.readString();
        return new QName(namespaceURI, localPart, prefix);
    }

    @Override
    public QName copy(Kryo kryo, QName original) {
        String namespaceURI = original.getNamespaceURI();
        String localPart = original.getLocalPart();
        String prefix = original.getPrefix();
        return new QName(namespaceURI, localPart, prefix);
    }
}
