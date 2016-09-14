package no.rutebanken.anshar.routes.siri.transformer;

import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;

public class SiriValueTransformer {

    private static Logger logger = LoggerFactory.getLogger(SiriValueTransformer.class);

    /**
     *
     * @param xml
     * @param adapters
     * @return
     * @throws JAXBException
     */
    public static Siri parseXml(String xml, List<ValueAdapter> adapters) throws JAXBException {
        return transform(SiriXml.parseXml(xml), adapters);
    }

    public static Siri parseXml(String xml) throws JAXBException {
        return SiriXml.parseXml(xml);
    }

    public static Siri transform(Siri siri, List<ValueAdapter> adapters) {

        if (siri != null && adapters != null) {
            adapters.forEach(a -> {
                try {
                    applyAdapter(siri, a);
                } catch (Throwable t) {
                    logger.warn("Caught exception while transforming SIRI-object.", t);
                }
            });
        }
        return siri;
    }

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
                            Object transformedValue = adapter.apply(value);

                            Method valueSetter = previousValue.getClass().getMethod("setValue", String.class);
                            valueSetter.invoke(previousValue, transformedValue);
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
