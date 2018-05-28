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

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static junit.framework.TestCase.fail;

public class CustomValidatorTest {

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

            InputStream stream = new ByteArrayInputStream(xml.getBytes("utf-8"));

            Document xmlDocument = builder.parse(stream);
            return xmlDocument.getFirstChild();
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }
}
