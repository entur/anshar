package no.rutebanken.anshar.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecureXmlFactoriesTest {

    /**
     * Classic XXE payload: declares an external entity pointing at a local file.
     * A securely configured parser must refuse the DOCTYPE/entity declaration.
     */
    private static final String XXE_XML =
            "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>" +
            "<foo>&xxe;</foo>";

    private static final String VALID_XML = "<foo><bar>baz</bar></foo>";

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void documentBuilderRejectsDoctypeDeclaration() throws Exception {
        DocumentBuilder builder = SecureXmlFactories.secureDocumentBuilderFactory().newDocumentBuilder();

        assertThrows(SAXException.class, () -> builder.parse(stream(XXE_XML)));
    }

    @Test
    public void documentBuilderParsesValidXml() throws Exception {
        DocumentBuilder builder = SecureXmlFactories.secureDocumentBuilderFactory().newDocumentBuilder();

        Document doc = builder.parse(stream(VALID_XML));

        assertNotNull(doc.getDocumentElement());
    }

    @Test
    public void xmlInputFactoryRejectsDtd() {
        assertThrows(XMLStreamException.class, () -> {
            XMLStreamReader reader =
                    SecureXmlFactories.secureXmlInputFactory().createXMLStreamReader(stream(XXE_XML));
            while (reader.hasNext()) {
                reader.next();
            }
        });
    }

    @Test
    public void xmlInputFactoryParsesValidXml() throws Exception {
        XMLStreamReader reader =
                SecureXmlFactories.secureXmlInputFactory().createXMLStreamReader(stream(VALID_XML));

        int elements = 0;
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                elements++;
            }
        }

        assertTrue(elements >= 2);
    }
}