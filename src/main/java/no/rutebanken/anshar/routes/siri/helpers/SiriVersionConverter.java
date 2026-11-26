package no.rutebanken.anshar.routes.siri.helpers;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.sax.SAXSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts SIRI 2.1 objects to SIRI 2.0 by marshalling the 2.1 object straight into the 2.0 unmarshaller as SAX
 * events, so no XML text is built: a whole delivery as text is large enough to land in the old generation as
 * humongous objects and lengthen GC pauses. Gives the same result as parsing the 2.1 XML text as SIRI 2.0: both
 * versions use the same namespace, the events are the ones a parser would report from the text, and elements the 2.0
 * model does not know are skipped as they are when parsing, since neither validates against the schema.
 * <p>
 * Runs on the calling thread and holds only SAX state. Piping the text through a stream into the parser would need a
 * second thread per conversion, and mapping 2.1 objects to 2.0 by hand would have to follow every model change.
 */
public final class SiriVersionConverter {

    // Expensive to create and thread-safe, so created once; marshallers and unmarshallers are neither
    private static final JAXBContext SIRI21 = context(uk.org.siri.siri21.Siri.class);
    private static final JAXBContext SIRI20 = context(uk.org.siri.siri20.Siri.class);

    private SiriVersionConverter() {
    }

    /** The SIRI 2.0 object with the same content; elements SIRI 2.0 does not know are dropped. The input is not changed. */
    public static uk.org.siri.siri20.Siri toSiri20(uk.org.siri.siri21.Siri siri) throws JAXBException {
        // Through an XMLReader rather than getUnmarshallerHandler(): the unmarshaller then leaves interning names to
        // the reader, instead of calling String.intern() on every name, which made large deliveries ~45% slower
        Object result = SIRI20.createUnmarshaller().unmarshal(new SAXSource(new MarshallingReader(siri), new InputSource()));
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

    /** Reports the SIRI 2.1 object as SAX events, with every name interned, as the string-interning feature promises */
    private static final class MarshallingReader implements XMLReader {

        private static final String STRING_INTERNING = "http://xml.org/sax/features/string-interning";
        private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";

        private final uk.org.siri.siri21.Siri siri;
        private ContentHandler contentHandler;
        private ErrorHandler errorHandler;
        private DTDHandler dtdHandler;
        private EntityResolver entityResolver;

        MarshallingReader(uk.org.siri.siri21.Siri siri) {
            this.siri = siri;
        }

        @Override
        public void parse(InputSource ignored) throws SAXException {
            try {
                SIRI21.createMarshaller().marshal(siri, new InterningHandler(contentHandler));
            } catch (JAXBException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void parse(String systemId) throws SAXException {
            parse(new InputSource(systemId));
        }

        @Override
        public boolean getFeature(String name) {
            return STRING_INTERNING.equals(name) || NAMESPACES.equals(name);
        }

        @Override
        public void setFeature(String name, boolean value) {
            // The events are fixed: namespace-aware, names interned
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public void setProperty(String name, Object value) {
            // No properties apply
        }

        @Override
        public void setContentHandler(ContentHandler handler) {
            this.contentHandler = handler;
        }

        @Override
        public ContentHandler getContentHandler() {
            return contentHandler;
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            this.errorHandler = handler;
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public void setDTDHandler(DTDHandler handler) {
            this.dtdHandler = handler;
        }

        @Override
        public DTDHandler getDTDHandler() {
            return dtdHandler;
        }

        @Override
        public void setEntityResolver(EntityResolver resolver) {
            this.entityResolver = resolver;
        }

        @Override
        public EntityResolver getEntityResolver() {
            return entityResolver;
        }
    }

    /**
     * Interns names on their way to the unmarshaller through a cache, so each distinct name costs one
     * String.intern() rather than one per occurrence. Names from the SIRI model are few; the cache stops growing at
     * {@link #MAX_CACHED_NAMES} in case extension content brings arbitrary ones.
     */
    // TODO: Profile a large ET downgrade in prd. If this handler shows up, cut its per-element cost: it does a map
    //  lookup per name (about four per start/end element) and copies every element's attributes into a new
    //  AttributesImpl, which adds up to millions of lookups and allocations for a 5,000-journey delivery.
    private static final class InterningHandler implements ContentHandler {

        private static final int MAX_CACHED_NAMES = 10_000;
        private static final Map<String, String> NAMES = new ConcurrentHashMap<>();

        private final ContentHandler target;

        InterningHandler(ContentHandler target) {
            this.target = target;
        }

        private static String intern(String name) {
            if (name == null) {
                return null;
            }
            String interned = NAMES.get(name);
            if (interned != null) {
                return interned;
            }
            interned = name.intern();
            if (NAMES.size() < MAX_CACHED_NAMES) {
                NAMES.putIfAbsent(interned, interned);
            }
            return interned;
        }

        private static Attributes intern(Attributes attributes) {
            if (attributes.getLength() == 0) {
                return attributes;
            }
            AttributesImpl interned = new AttributesImpl();
            for (int i = 0; i < attributes.getLength(); i++) {
                interned.addAttribute(intern(attributes.getURI(i)), intern(attributes.getLocalName(i)),
                        intern(attributes.getQName(i)), attributes.getType(i), attributes.getValue(i));
            }
            return interned;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            target.setDocumentLocator(locator);
        }

        @Override
        public void startDocument() throws SAXException {
            target.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            target.endDocument();
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            target.startPrefixMapping(intern(prefix), intern(uri));
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            target.endPrefixMapping(intern(prefix));
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            target.startElement(intern(uri), intern(localName), intern(qName), intern(attributes));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            target.endElement(intern(uri), intern(localName), intern(qName));
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            target.characters(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            target.ignorableWhitespace(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            this.target.processingInstruction(target, data);
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            target.skippedEntity(name);
        }
    }
}
