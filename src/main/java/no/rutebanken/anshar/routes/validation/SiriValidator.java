package no.rutebanken.anshar.routes.validation;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import uk.org.siri.siri20.Siri;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SiriValidator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static JAXBContext jaxbContext;
    private static Schema schema;


    /**
     * Keeps a list of references to unique ids
     */
    @Autowired
    @Qualifier("getValidationResultRefMap")
    private IMap<String, List<String>> validationResultRefs;

    @Autowired
    @Qualifier("getValidationResultSiriMap")
    private IMap<String, Siri> validatedSiri;

    @Autowired
    @Qualifier("getValidationResultJsonMap")
    private IMap<String, JSONObject> validationResults;


    private int maxResultsPerSubscription = 100;

    public SiriValidator() {
        if (jaxbContext == null) {
            try {
                jaxbContext = JAXBContext.newInstance(Siri.class);

                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

                schema = sf.newSchema(getClass().getClassLoader().getResource("siri-2.0/xsd/siri.xsd"));

            } catch (JAXBException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            }
        }
    }

    public Siri parseXml(SubscriptionSetup subscriptionSetup, InputStream xml) {
        try {
            long t1 = System.currentTimeMillis();
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            SiriValidationEventHandler handler = new SiriValidationEventHandler();
subscriptionSetup.setValidation(true);
            if (subscriptionSetup.isValidation()) {
                unmarshaller.setSchema(schema);
                unmarshaller.setEventHandler(handler);
            }

            XMLStreamReader reader =  XMLInputFactory.newInstance().createXMLStreamReader(xml);
            System.out.println("Reader: " + reader.getClass());
            Siri siri = unmarshaller.unmarshal(reader, Siri.class).getValue();

            System.err.println("Parsing XML took " + (System.currentTimeMillis()-t1) + ", validation: " + subscriptionSetup.isValidation());
            if (subscriptionSetup.isValidation()) {
                addResult(subscriptionSetup, siri, handler.toJSON());
            }
            System.err.println("Parsing and adding XML took " + (System.currentTimeMillis()-t1) + ", validation: " + subscriptionSetup.isValidation());
            return siri;
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addResult(SubscriptionSetup subscriptionSetup, Siri siri, JSONObject jsonObject) {

        List<String> subscriptionValidationRefs = validationResultRefs.getOrDefault(subscriptionSetup.getSubscriptionId(), new ArrayList<>());

        String newUniqueReference = UUID.randomUUID().toString();

        while (subscriptionValidationRefs.size() >= maxResultsPerSubscription) {
            // remove oldest validation
            String ref = subscriptionValidationRefs.get(0);
            validatedSiri.delete(ref);
            validationResults.delete(ref);
            subscriptionValidationRefs.remove(0);
        }

        subscriptionValidationRefs.add(newUniqueReference);

        validatedSiri.set(newUniqueReference, siri);
        validationResults.set(newUniqueReference, jsonObject);
        validationResultRefs.set(subscriptionSetup.getSubscriptionId(), subscriptionValidationRefs);
    }
}
