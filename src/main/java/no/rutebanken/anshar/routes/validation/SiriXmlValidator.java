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

package no.rutebanken.anshar.routes.validation;

import com.hazelcast.core.IMap;
import com.hazelcast.core.ReplicatedMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.org.siri.siri20.Siri;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static no.rutebanken.anshar.util.CompressionUtil.compress;

@Component
@Configuration
public class SiriXmlValidator extends ApplicationContextHolder{

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static JAXBContext jaxbContext;
    private static Schema schema;

    @Autowired
    private AnsharConfiguration configuration;

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
    @Qualifier("getValidationSizeTracker")
    private IMap<String, Long> validationSize;

    @Autowired
    @Qualifier("getValidationResultJsonMap")
    private IMap<String, JSONObject> validationResults;

    @Autowired
    @Qualifier("getSubscriptionsMap")
    private ReplicatedMap<String, SubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getValidationFilterMap")
    private ReplicatedMap<String, String> validationFilters;

    @Autowired
    private SubscriptionManager subscriptionManager;

    private final Map<SiriDataType, Set<CustomValidator>> validationRules = new HashMap<>();

    public SiriXmlValidator() {
        if (jaxbContext == null) {
            try {
                jaxbContext = JAXBContext.newInstance(Siri.class);

                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

                schema = sf.newSchema(Siri.class.getClassLoader().getResource("siri-2.0/xsd/siri.xsd"));

            } catch (JAXBException | SAXException e) {
                e.printStackTrace();
            }
        }
    }


    private void populateValidationRules() {
        Map<String, Object> validatorBeans = getContext().getBeansWithAnnotation(Validator.class);

        for (Object o : validatorBeans.values()) {
            if (o instanceof CustomValidator) {
                final String profileName = o.getClass().getAnnotation(Validator.class).profileName();
                if (profileName.equals(configuration.getValidationProfileName())) {
                    final SiriDataType type = o.getClass().getAnnotation(Validator.class).targetType();

                    final Set<CustomValidator> validators = validationRules.getOrDefault(type, new HashSet<>());
                    validators.add((CustomValidator) o);
                    validationRules.put(type, validators);
                }
            }
        }
    }


    public Siri parseXml(SubscriptionSetup subscriptionSetup, InputStream xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(xml);

            Siri siri = unmarshaller.unmarshal(reader, Siri.class).getValue();

            if (siri.getServiceDelivery() != null && subscriptionSetup.isValidation()) {

                new Thread(() -> {
                    try {
                        long t1 = System.currentTimeMillis();

                        /*
                         * Setting SIRI-datatype based on subscription
                         */
                        SiriDataType type = subscriptionSetup.getSubscriptionType();
                        if (type == null) {
                            return;
                        }


                        /*

                           Re-marshalling - and unmarshalling - object to ensure correct line numbers.

                         */
                        Marshaller marshaller = jaxbContext.createMarshaller();
                        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                        StringWriter writer = new StringWriter();
                        marshaller.marshal(siri, writer);

                        String originalXml = writer.toString();

                        if (subscriptionSetup.getValidationFilter() != null && !subscriptionSetup.getValidationFilter().isEmpty()) {
                            if (!originalXml.contains(subscriptionSetup.getValidationFilter())) {
                                logger.info("Incoming XML does not contain \"{}\", skip validation for this request.", subscriptionSetup.getValidationFilter());
                                return;
                            }
                        }

                        StringReader sr= new StringReader(originalXml);

                        SiriValidationEventHandler handler = new SiriValidationEventHandler();
                        SiriValidationEventHandler profileHandler = new SiriValidationEventHandler();

                        unmarshaller.setSchema(schema);
                        unmarshaller.setEventHandler(handler);

                        //Unmarshalling with schema-validation
                        unmarshaller.unmarshal(sr);

                        if (configuration.isProfileValidation()) {
                            // Custom validation of attribute contents
                            validateAttributes(originalXml, type, profileHandler);
                        }

                        JSONObject schemaEvents = handler.toJSON();
                        JSONObject profileEvents = profileHandler.toJSON();

                        JSONObject combinedEvents = new JSONObject();
                        combinedEvents.put("schema", schemaEvents);
                        combinedEvents.put("profile", profileEvents);

                        addResult(subscriptionSetup, originalXml, combinedEvents);

                        logger.info("Validation took: " + (System.currentTimeMillis()-t1));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

            }

            return siri;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void validateAttributes(String siri, SiriDataType type, SiriValidationEventHandler handler) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        if (validationRules.isEmpty()) {
            populateValidationRules();
        }
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();

        InputStream stream = new ByteArrayInputStream(siri.getBytes(StandardCharsets.UTF_8));

        Document xmlDocument = builder.parse(stream);

        int errorCounter = 0;
        for (CustomValidator rule : validationRules.get(type)) {
            NodeList nodes = (NodeList) xpath.evaluate(rule.getXpath(), xmlDocument, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                ValidationEvent event = rule.isValid(nodes.item(i));
                if (event != null) {
                    handler.handleCategorizedEvent(rule.getCategoryName(), event);
                    errorCounter++;
                }
            }
        }
        logger.info("Found {} custom rule violations in SIRI XML", errorCounter);
    }

    public void clearValidationResults(String subscriptionId) {
        List<String> validationRefs = validationResultRefs.get(subscriptionId);

        if (validationRefs != null) {
            for (String ref : validationRefs) {
                validationResults.delete(ref);
                validatedSiri.delete(ref);
            }

            validationSize.delete(subscriptionId);
            validationResultRefs.delete(subscriptionId);
        }
    }

    public void addFilter(String subscriptionId, String validationFilter) {
        validationFilters.remove(subscriptionId);
        if (validationFilter != null && !validationFilter.isEmpty()) {
            validationFilters.put(subscriptionId, validationFilter);
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

        if (validationFilters.containsKey(subscriptionId)) {
            String filter = validationFilters.get(subscriptionId);

            validationResult.put("filter", filter);
        }

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
        JSONObject jsonObject = validationResults.getOrDefault(validationRef, new JSONObject());
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
            try (ObjectInputStream objectIn = new ObjectInputStream(gzipIn)) {
                return (String) objectIn.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
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

        } catch (XMLStreamException | JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addResult(SubscriptionSetup subscriptionSetup, String siriXml, JSONObject jsonObject) throws IOException {

        List<String> subscriptionValidationRefs = validationResultRefs.getOrDefault(subscriptionSetup.getSubscriptionId(), new ArrayList<>());

        String newUniqueReference = UUID.randomUUID().toString();

        subscriptionValidationRefs.add(newUniqueReference);

        // GZIP'ing contents to reduce memory-footprint
        byte[] byteArray = compress(siriXml);

        final Long totalXmlSize = (validationSize.getOrDefault(subscriptionSetup.getSubscriptionId(), 0L) + byteArray.length);

        validatedSiri.set(newUniqueReference, byteArray, configuration.getNumberOfHoursToKeepValidation(), TimeUnit.HOURS);
        validationResults.set(newUniqueReference, jsonObject, configuration.getNumberOfHoursToKeepValidation(), TimeUnit.HOURS);
        validationResultRefs.set(subscriptionSetup.getSubscriptionId(), subscriptionValidationRefs, configuration.getNumberOfHoursToKeepValidation(), TimeUnit.HOURS);
        validationSize.set(subscriptionSetup.getSubscriptionId(), totalXmlSize, configuration.getNumberOfHoursToKeepValidation(), TimeUnit.HOURS);

        if (totalXmlSize > (configuration.getMaxTotalXmlSizeOfValidation() * 1024*1024)) {
            subscriptionSetup.setValidation(false);
            subscriptionManager.updateSubscription(subscriptionSetup);
            logger.info("Reached max size - {}mb - for validations, validated {} deliveries,  disabling validation for {}", configuration.getMaxTotalXmlSizeOfValidation(), subscriptionValidationRefs.size(), subscriptionSetup);
        }
        if (subscriptionValidationRefs.size() >= configuration.getMaxNumberOfValidations()) {
            subscriptionSetup.setValidation(false);
            subscriptionManager.updateSubscription(subscriptionSetup);
            logger.info("Reached max number of validations, validated {} deliveries, disabling validation for {}", subscriptionValidationRefs.size(), subscriptionSetup);
        }
    }
}
