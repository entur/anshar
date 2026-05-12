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
