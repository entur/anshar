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

package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.ValidationEvent;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class CustomValidatorTest extends SpringBootBaseTest {

    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();

    protected String createXml(String fieldName, String value) {
        return "<" + fieldName + ">" + value + "</" + fieldName + ">";
    }

    protected  String mergeXml(String... elements) {
        StringBuilder b = new StringBuilder("<PLACEHOLDER>");
        for (String element : elements) {
            b.append(element);
        }
        b.append("</PLACEHOLDER>");
        return b.toString();
    }

    protected Node createXmlNode(String fieldName, String value){
        return createXmlNode(createXml(fieldName,value));
    }

    protected Node createXmlNode(String xml){
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();

            InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

            Document xmlDocument = builder.parse(stream);
            return xmlDocument.getFirstChild();
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }

    @Test
    public void testInvalidFields() {
        CustomValidator validator = new CustomValidator() {
            @Override
            public String getXpath() {
                return null;
            }

            @Override
            public ValidationEvent isValid(Node node) {
                return null;
            }
        };

        String validElementXml = createXml("allowedElement", "valid");
        String invalidElementXml = createXml("invalidElement", "valid");


        Node node = createXmlNode(mergeXml(validElementXml, invalidElementXml));

        ValidationEvent event = validator.verifyNonExistingFields(node, "TestElement", "dummyElement");

        assertNull(event);

        event = validator.verifyNonExistingFields(node, "TestElement", "invalidElement");

        assertNotNull(event);
        assertTrue(event.getMessage().contains("invalidElement"));
        assertFalse(event.getMessage().contains("allowedElement"));
    }

    protected Document createXmlDocument(String xml) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();

        InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        return builder.parse(stream);

    }

    protected NodeList getMatchingNodelist(Document xmlDocument, String ruteXpath) throws XPathExpressionException {
        return (NodeList) xpath.evaluate(ruteXpath, xmlDocument, XPathConstants.NODESET);
    }
}
