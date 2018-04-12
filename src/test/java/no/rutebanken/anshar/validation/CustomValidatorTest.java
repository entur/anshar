package no.rutebanken.anshar.validation;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CustomValidatorTest {

    protected String createXml(String fieldName, String value) {
        return "<" + fieldName + ">" + value + "</" + fieldName + ">";
    }


    protected Node createXmlNode(String xml) throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();

        InputStream stream = new ByteArrayInputStream(xml.getBytes("utf-8"));

        Document xmlDocument = builder.parse(stream);
        return xmlDocument.getFirstChild();
    }
}
