package no.rutebanken.anshar.routes.siri.helpers;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshallerHandler;

/**
 * Converts SIRI 2.1 objects to SIRI 2.0 by marshalling the 2.1 object straight into the 2.0 unmarshaller as SAX
 * events, so no XML text is built: a whole delivery as text is large enough to land in the old generation as
 * humongous objects and lengthen GC pauses. Gives the same result as parsing the 2.1 XML text as SIRI 2.0.
 * See docs/superpowers/specs/2026-11-18-siri20-downgrade-without-xml-text-design.md.
 */
public final class SiriVersionConverter {

    // Expensive to create and thread-safe, so created once; marshallers and unmarshallers are neither
    private static final JAXBContext SIRI21 = context(uk.org.siri.siri21.Siri.class);
    private static final JAXBContext SIRI20 = context(uk.org.siri.siri20.Siri.class);

    private SiriVersionConverter() {
    }

    /** The SIRI 2.0 object with the same content; elements SIRI 2.0 does not know are dropped. The input is not changed. */
    public static uk.org.siri.siri20.Siri toSiri20(uk.org.siri.siri21.Siri siri) throws JAXBException {
        UnmarshallerHandler handler = SIRI20.createUnmarshaller().getUnmarshallerHandler();
        SIRI21.createMarshaller().marshal(siri, handler);
        Object result = handler.getResult();
        if (result instanceof JAXBElement<?> element) {
            result = element.getValue();
        }
        return (uk.org.siri.siri20.Siri) result;
    }

    private static JAXBContext context(Class<?> type) {
        try {
            return JAXBContext.newInstance(type);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
