package no.rutebanken.anshar.routes.siri.transformer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.PostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SiriValueTransformer {

    public static final String SEPARATOR = "$";

    private static Logger logger = LoggerFactory.getLogger(SiriValueTransformer.class);

    private static final LoadingCache<Class, List<Method>> getterMethodsCache = CacheBuilder.newBuilder()
            .build(
                    new CacheLoader<Class, List<Method>>() {
                        public List<Method> load(Class clazz) {
                            Method[] methods = clazz.getMethods();
                            List<Method> getterMethods = new ArrayList<>();
                            for (Method method : methods) {
                                if (method.getParameterCount() == 0 &&
                                        !("void".equals(method.getReturnType().getName()))) {
                                    getterMethods.add(method);
                                }
                            }
                            return getterMethods;
                        }
                    });

    private static final List<String> methodsToIgnore = Arrays.asList("getMonitoringError");

    /**
     *
     * @param xml
     * @param adapters
     * @return
     * @throws JAXBException
     */
    public static Siri parseXml(InputStream xml, List adapters) throws JAXBException, XMLStreamException {
        return transform(SiriXml.parseXml(xml), adapters);
    }

    public static Siri parseXml(InputStream xml) throws JAXBException, XMLStreamException {
        return SiriXml.parseXml(xml);
    }

    public static Siri transform(Siri siri, List<ValueAdapter> adapters) {
        if (siri == null) {
            return null;
        }
        long t1 = System.currentTimeMillis();
        Siri transformed;
        try {
        	transformed = SiriObjectFactory.deepCopy(siri);
        } catch (Exception e) {
            logger.warn("Unable to transform SIRI-object", e);
            return siri;
        } finally {
            long t2 = System.currentTimeMillis();

            logger.info("Deepcopy took {}ms ", (t2-t1));
        }
        if (transformed != null && adapters != null) {
            logger.trace("Applying {} valueadapters {}", adapters.size(), adapters);

            List<ValueAdapter> valueAdapters = new ArrayList<>();
            for (ValueAdapter adapter : adapters) {
                if (!(adapter instanceof PostProcessor)) {
                    valueAdapters.add(adapter);
                }
            }

            List<PostProcessor> postProcessors = new ArrayList<>();
            for (ValueAdapter valueAdapter : adapters) {
                if ((valueAdapter instanceof PostProcessor)) {
                    postProcessors.add((PostProcessor) valueAdapter);
                }
            }

            for (ValueAdapter a : valueAdapters) {
                try {
                    applyAdapter(transformed, a);
                } catch (Throwable t) {
                    logger.warn("Caught exception while transforming SIRI-object.", t);
                }
            }

            for (PostProcessor processor : postProcessors) {
                try {
                    processor.process(transformed);
                } catch (Throwable t) {
                    logger.warn("Caught exception while post-processing SIRI-object.", t);
                }
            }
        }
        long t3 = System.currentTimeMillis();

        logger.info("Transformation took {}ms ", (t3-t1));
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
    private static void applyAdapter(Object obj, ValueAdapter adapter) throws Throwable {

        //Only apply to Siri-classes
        if (obj.getClass().getName().startsWith("uk.org.siri")) {

            List<Method> allMethods = getterMethodsCache.get(obj.getClass());
            for (Method method : allMethods) {
                if (methodsToIgnore.contains(method.getName())) {
                    continue;
                }


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
                                alteredValue = adapter.apply(value.substring(value.indexOf(SEPARATOR) + SEPARATOR.length()));
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
                                applyAdapter(o, adapter);
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
