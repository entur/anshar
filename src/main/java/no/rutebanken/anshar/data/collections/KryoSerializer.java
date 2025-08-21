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
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.hazelcast.nio.serialization.ByteArraySerializer;
import org.objenesis.strategy.StdInstantiatorStrategy;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class KryoSerializer implements ByteArraySerializer {

    private static final KryoPool kryoPool;

    static {
        KryoFactory factory = () -> {
            Kryo kryo = new Kryo();
            kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
            kryo.register(QName.class, new QNameSerializer());
            return kryo;
        };

        kryoPool = new KryoPool.Builder(factory).softReferences().build();
    }

    @Override
    public byte[] write(Object o) {
        Kryo kryo = kryoPool.borrow();
        try {
                ByteArrayOutputStream byteArrayOutputStream =
                    new ByteArrayOutputStream();
                DeflaterOutputStream deflaterOutputStream =
                    new DeflaterOutputStream(byteArrayOutputStream);
                Output output = new Output(deflaterOutputStream);
                kryo.writeClassAndObject(output, o);
                output.close();

            return byteArrayOutputStream.toByteArray();
        } finally {
            kryoPool.release(kryo);
        }
    }

    @Override
    public Object read(byte[] bytes) {
        Kryo kryo = kryoPool.borrow();

        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            InflaterInputStream in = new InflaterInputStream(byteArrayInputStream);

            Input input = new Input(in);
            return kryo.readClassAndObject(input);
        } finally {
            kryoPool.release(kryo);
        }
    }

    @Override
    public int getTypeId() {
        return 1;
    }

    @Override
    public void destroy() {
        //Ignore d
    }
}

