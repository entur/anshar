package no.rutebanken.anshar.routes.siri.transformer;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.objenesis.strategy.StdInstantiatorStrategy;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import uk.org.siri.siri20.Siri;

public class SiriValueTransformer {

    public static final String SEPARATOR = "$";
    
    private static Logger logger = LoggerFactory.getLogger(SiriValueTransformer.class);

    /**
     *
     * @param xml
     * @param adapters
     * @return
     * @throws JAXBException
     */
    public static Siri parseXml(InputStream xml, List<ValueAdapter> adapters) throws JAXBException {
        return transform(SiriXml.parseXml(xml), adapters);
    }

    public static Siri parseXml(InputStream xml) throws JAXBException {
        return SiriXml.parseXml(xml);
    }

    public static Siri transform(Siri siri, List<ValueAdapter> adapters) {
        if (siri == null) {
            return null;
        }
        
        
        
        Siri transformed;
        try {
        	transformed = SiriObjectFactory.deepCopy(siri);
        } catch (Exception e) {
            logger.warn("Unable to transform SIRI-object", e);
            return siri;
        }
        if (transformed != null && adapters != null) {
            logger.info("Applying {} valueadapters {}", adapters.size(), adapters);
            adapters.forEach(a -> {
                try {
                    applyAdapter(transformed, a);
                } catch (Throwable t) {
                    logger.warn("Caught exception while transforming SIRI-object.", t);
                }
            });
        }
        return transformed;
    }

    /**
     * Recursively applies ValueAdapter to all fields of the specified type within SIRI-packages.
     *
     * Uses getValue()/setValue(...) apply adapters
     *
     * @param obj
     * @param adapter
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    private static void applyAdapter(Object obj, ValueAdapter adapter) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        if (obj.getClass().getName().startsWith("uk.org.siri")) {
            //Only apply to Siri-classes

            Method[] methods = obj.getClass().getMethods();
            for (Method method : methods) {
                if (method.getParameterCount() == 0 &&
                        !("void".equals(method.getReturnType().getName()))) {
                    if (method.getReturnType().equals(adapter.getClassToApply())) {
                        Object previousValue = method.invoke(obj);
                        if (previousValue != null) {
                            String value = (String) previousValue.getClass().getMethod("getValue").invoke(previousValue);
                            String alteredValue;

                            String originalId = value;

                            if (adapter instanceof OutboundIdAdapter) {
                                alteredValue = adapter.apply(value);
                            } else {
                                if (value.contains(SEPARATOR)) {
                                    originalId = value.substring(0, value.indexOf(SEPARATOR));
                                    alteredValue = adapter.apply(value.substring(value.indexOf(SEPARATOR)+SEPARATOR.length()));
                                } else {
                                    alteredValue = adapter.apply(value);
                                }
                                alteredValue = originalId + SEPARATOR + alteredValue;
                            }


                            Method valueSetter = previousValue.getClass().getMethod("setValue", String.class);
                            valueSetter.invoke(previousValue, alteredValue);
                        }
                    } else {
                        Object currentValue = method.invoke(obj);
                        if (currentValue != null) {
                            if (currentValue instanceof List) {
                                List list = (List) currentValue;
                                for (Object o : list) {
                                    applyAdapter(o,  adapter);
                                }
                            } else {
                                applyAdapter(currentValue, adapter);
                            }
                        }
                    }
                }
            }
        }
    }
}
