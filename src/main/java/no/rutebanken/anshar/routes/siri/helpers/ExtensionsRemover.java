package no.rutebanken.anshar.routes.siri.helpers;

import jakarta.xml.bind.JAXBElement;
import uk.org.siri.siri21.Extensions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Removes SIRI Extensions from all levels of a SIRI-object.
 * Extensions are DOM-elements, which are not thread safe - not even for reading - so objects that are serialized
 * concurrently intermittently fail with NullPointerException and StackOverflowError.
 */
public final class ExtensionsRemover {

    private static final String SIRI_PACKAGE_PREFIX = "uk.org.siri.";

    // Fields that may contain Extensions - directly or further down - per SIRI-class
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private ExtensionsRemover() {
    }

    /**
     * @return true if any Extensions were removed
     */
    public static boolean removeExtensions(Object siriObject) {
        if (siriObject == null) {
            return false;
        }
        if (siriObject instanceof JAXBElement<?> jaxbElement) {
            return removeExtensions(jaxbElement.getValue());
        }
        if (siriObject instanceof Collection<?> collection) {
            boolean removed = false;
            for (Object element : collection) {
                removed |= removeExtensions(element);
            }
            return removed;
        }
        if (!isSiriClass(siriObject.getClass())) {
            return false;
        }

        boolean removed = false;
        for (Field field : FIELDS.computeIfAbsent(siriObject.getClass(), ExtensionsRemover::findCandidateFields)) {
            try {
                Object value = field.get(siriObject);
                if (value == null) {
                    continue;
                }
                if (value instanceof Extensions) {
                    field.set(siriObject, null);
                    removed = true;
                } else {
                    removed |= removeExtensions(value);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to access " + field, e);
            }
        }
        return removed;
    }

    private static List<Field> findCandidateFields(Class<?> siriClass) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = siriClass; c != null && isSiriClass(c); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && mayContainExtensions(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static boolean mayContainExtensions(Class<?> type) {
        return type == Extensions.class
                || type == Object.class
                || Collection.class.isAssignableFrom(type)
                || JAXBElement.class.isAssignableFrom(type)
                || (isSiriClass(type) && !type.isEnum());
    }

    private static boolean isSiriClass(Class<?> type) {
        return type.getName().startsWith(SIRI_PACKAGE_PREFIX) && !type.isEnum();
    }
}
