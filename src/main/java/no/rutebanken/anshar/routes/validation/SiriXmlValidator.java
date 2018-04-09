package no.rutebanken.anshar.routes.validation;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import uk.org.siri.siri20.Siri;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
@Configuration
public class SiriXmlValidator {

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
    private IMap<String, byte[]> validatedSiri;

    @Autowired
    @Qualifier("getValidationResultJsonMap")
    private IMap<String, JSONObject> validationResults;

    @Autowired
    @Qualifier("getSubscriptionsMap")
    private IMap<String, SubscriptionSetup> subscriptions;

    @Value("${anshar.validation.max.results:5}")
    private int maxResultsPerSubscription;

    public SiriXmlValidator() {
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
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(xml);

            Siri siri = unmarshaller.unmarshal(reader, Siri.class).getValue();

            if (subscriptionSetup.isValidation()) {


                long t1 = System.currentTimeMillis();

                /*

                   Re-marshalling - and unmarshalling - object to ensure correct line numbers.

                 */

                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                StringWriter writer = new StringWriter();
                marshaller.marshal(siri, writer);

                String originalXml = writer.toString();

                StringReader sr= new StringReader(originalXml);

                SiriValidationEventHandler handler = new SiriValidationEventHandler();

                unmarshaller.setSchema(schema);
                unmarshaller.setEventHandler(handler);
                unmarshaller.unmarshal(sr);

                addResult(subscriptionSetup, originalXml, handler.toJSON());

                System.err.println("Validation took: " + (System.currentTimeMillis()-t1));
            }

            return siri;
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void clearValidationResults(String subscriptionId) {
        List<String> validationRefs = validationResultRefs.get(subscriptionId);

        if (validationRefs != null) {
            for (String ref : validationRefs) {
                validationResults.delete(ref);
                validatedSiri.delete(ref);
            }

            validationResultRefs.delete(subscriptionId);
        }
    }
    public JSONObject getValidationResults(String subscriptionId) {
        List<String> validationRefs = validationResultRefs.get(subscriptionId);

        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup == null) {
            return null;
        }

        JSONObject validationResult = new JSONObject();

        validationResult.put("subscription", subscriptionSetup.toJSON());

        JSONArray resultList = new JSONArray();
        if (validationRefs != null) {
            for (String ref : validationRefs) {
                resultList.add(getJsonValidationResults(ref));
            }
        }
        validationResult.put("validationRefs", resultList);

        return validationResult;
    }

    private JSONObject getJsonValidationResults(String validationRef) {
        JSONObject jsonObject = validationResults.get(validationRef);
        jsonObject.put("validationRef", validationRef);
        return jsonObject;
    }

    public String getValidatedSiri(String validationRef) throws IOException {
        return unzipString(validatedSiri.get(validationRef));
    }

    private String unzipString(byte[] compressed) throws IOException {
        if (compressed != null) {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressed);
            GZIPInputStream gzipIn = new GZIPInputStream(byteArrayInputStream);
            ObjectInputStream objectIn = new ObjectInputStream(gzipIn);
            try {
                return (String) objectIn.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                objectIn.close();
            }
        }
        return null;
    }

    public static JSONObject validate(InputStream xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            SiriValidationEventHandler handler = new SiriValidationEventHandler();

            unmarshaller.setSchema(schema);
            unmarshaller.setEventHandler(handler);

            XMLStreamReader reader =  XMLInputFactory.newInstance().createXMLStreamReader(xml);

            unmarshaller.unmarshal(reader, Siri.class).getValue();

            return handler.toJSON();

        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addResult(SubscriptionSetup subscriptionSetup, String siriXml, JSONObject jsonObject) throws IOException {

        List<String> subscriptionValidationRefs = validationResultRefs.getOrDefault(subscriptionSetup.getSubscriptionId(), new ArrayList<>());

        String newUniqueReference = UUID.randomUUID().toString();

        subscriptionValidationRefs.add(newUniqueReference);

        // GZIP'ing contents to reduce memory-footprint
        byte[] byteArray = zipString(siriXml);

        validatedSiri.set(newUniqueReference, byteArray);
        validationResults.set(newUniqueReference, jsonObject);
        validationResultRefs.set(subscriptionSetup.getSubscriptionId(), subscriptionValidationRefs);

        if (subscriptionValidationRefs.size() >= maxResultsPerSubscription) {
            subscriptionSetup.setValidation(false);
            logger.info("Reached {} validations - disabling validation for {}", maxResultsPerSubscription, subscriptionSetup);
        }
    }

    private byte[] zipString(String siriXml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut);
        objectOut.writeObject(siriXml);
        objectOut.close();

        return baos.toByteArray();
    }

}
