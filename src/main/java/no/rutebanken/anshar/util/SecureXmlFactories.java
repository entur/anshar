package no.rutebanken.anshar.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;

/**
 * Factory methods for XML parsers hardened against XXE (XML External Entity)
 * and DTD-based attacks. All inbound SIRI-XML originates from external providers
 * and must therefore never be parsed with the permissive JDK defaults.
 */
public final class SecureXmlFactories {

    private SecureXmlFactories() {
    }

    public static XMLInputFactory secureXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Disable DTDs entirely - also blocks "billion laughs" entity-expansion DoS.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    public static DocumentBuilderFactory secureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            // Primary defense: refuse any document that declares a DOCTYPE.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not configure secure DocumentBuilderFactory", e);
        }
        return factory;
    }
}