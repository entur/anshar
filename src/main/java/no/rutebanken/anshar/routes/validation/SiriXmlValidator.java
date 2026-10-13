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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.ProfileValidationEventOrList;
import no.rutebanken.anshar.routes.validation.validators.SiriObjectValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import no.rutebanken.anshar.util.SecureXmlFactories;
import uk.org.siri.siri21.Siri;

import javax.xml.XMLConstants;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.util.Collections.EMPTY_LIST;
import static no.rutebanken.anshar.routes.validation.ValidationType.PROFILE_VALIDATION;
import static no.rutebanken.anshar.routes.validation.ValidationType.SCHEMA_VALIDATION;
import static no.rutebanken.anshar.util.CompressionUtil.compress;

@Component
@Configuration
public class SiriXmlValidator extends ApplicationContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(SiriXmlValidator.class);

    private static JAXBContext jaxbContext;
    private static Schema schema;
    private static final XMLInputFactory xmlInputFactory = SecureXmlFactories.secureXmlInputFactory();
    private static final DocumentBuilderFactory builderFactory = SecureXmlFactories.secureDocumentBuilderFactory();


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

    @Autowired
    private PrometheusMetricsService metricsService;

    private volatile Map<SiriDataType, Set<CustomValidator>> validationRules;

    private static final int CLEANUP_INTERVAL_MINUTES = 10;

    @Value("${anshar.validation.concurrent.threads:5}")
    private int maxConcurrentValidations;

    @Value("${anshar.validation.queue.size:100}")
    private int maxQueuedValidations;

    private final Path validationDumpDirectory = Path.of(System.getProperty("java.io.tmpdir"), "anshar-validation");

    private ExecutorService validationExecutorService;

    private ScheduledExecutorService cleanupScheduler;

    @PostConstruct
    private void initExecutors() {
        ThreadFactory factory = new ThreadFactoryBuilder()
            .setNameFormat("validation")
            .setDaemon(true)
            .build();

        // Threads and queue are bounded to limit memory usage - validation is skipped when the queue is full
        final AtomicLong skippedCounter = new AtomicLong();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            maxConcurrentValidations,
            maxConcurrentValidations,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(maxQueuedValidations),
            factory,
            (runnable, rejectedBy) -> {
                long skipped = skippedCounter.incrementAndGet();
                if (skipped == 1 || skipped % 100 == 0) {
                    logger.warn("Validation queue is full - {} validations skipped so far", skipped);
                }
            }
        );
        executor.allowCoreThreadTimeOut(true);
        validationExecutorService = executor;

        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("validation-cleanup").setDaemon(true).build());
        cleanupScheduler.scheduleWithFixedDelay(() -> {
            try {
                removeExpiredDumpFiles();
                removeExpiredValidationResults();
            } catch (Throwable t) {
                //Catch everything to avoid executor being killed
                logger.info("Exception caught when cleaning up validation data", t);
            }
        }, CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    private void shutdownExecutors() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
        if (validationExecutorService != null) {
            validationExecutorService.shutdownNow();
        }
    }

    /**
     * @return Number of hours to keep validation data - minimum 1 hour, since validation data is never kept forever
     */
    private int getPersistHours() {
        return Math.max(1, configuration.getNumberOfHoursToKeepValidation());
    }

    /**
     * Deletes XML-dumps that are older than the configured persist-period
     */
    private void removeExpiredDumpFiles() throws IOException {
        if (!Files.isDirectory(validationDumpDirectory)) {
            return;
        }
        final Instant expiry = Instant.now().minus(getPersistHours(), ChronoUnit.HOURS);
        try (Stream<Path> files = Files.list(validationDumpDirectory)) {
            files.forEach(file -> {
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(expiry)) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    logger.info("Unable to delete {}: {}", file, e.toString());
                }
            });
        }
    }

    /**
     * Validation results expire after the configured persist-period - this removes the references to expired results
     */
    void removeExpiredValidationResults() {
        for (String subscriptionId : validationResultRefs.keySet()) {
            // Locked to avoid overwriting results that are added concurrently
            validationResultRefs.lock(subscriptionId);
            try {
                removeExpiredValidationResults(subscriptionId);
            } finally {
                validationResultRefs.unlock(subscriptionId);
            }
        }
    }

    private void removeExpiredValidationResults(String subscriptionId) {
        List<String> validationRefs = validationResultRefs.get(subscriptionId);
        if (validationRefs == null) {
            return;
        }

        List<String> remainingRefs = new ArrayList<>();
        for (String ref : validationRefs) {
            if (validationResults.containsKey(ref)) {
                remainingRefs.add(ref);
            } else {
                validatedSiri.delete(ref);
            }
        }

        if (remainingRefs.isEmpty()) {
            validationResultRefs.delete(subscriptionId);
            validationSize.delete(subscriptionId);
        } else if (remainingRefs.size() < validationRefs.size()) {
            long totalXmlSize = 0;
            for (String ref : remainingRefs) {
                Integer size = validatedSiri.executeOnKey(ref, new ValueLengthEntryProcessor());
                if (size != null) {
                    totalXmlSize += size;
                }
            }
            validationResultRefs.set(subscriptionId, remainingRefs);
            validationSize.set(subscriptionId, totalXmlSize);
        }
    }

    static {
        if (jaxbContext == null) {
            try {
                jaxbContext = JAXBContext.newInstance(Siri.class);

                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

                schema = sf.newSchema(Siri.class.getClassLoader().getResource("siri-2.1/xsd/siri.xsd"));

            } catch (JAXBException | SAXException e) {
                logger.warn("Caught exception when initializing validator", e);
            }
        }
    }

    /**
     * Rules are loaded on first use - the map is not published until it is complete, since validation is done by multiple threads
     */
    private Map<SiriDataType, Set<CustomValidator>> getValidationRules() {
        Map<SiriDataType, Set<CustomValidator>> rules = validationRules;
        if (rules == null) {
            synchronized (this) {
                rules = validationRules;
                if (rules == null) {
                    rules = loadValidationRules();
                    if (!rules.isEmpty()) {
                        validationRules = rules;
                    }
                }
            }
        }
        return rules;
    }

    private Map<SiriDataType, Set<CustomValidator>> loadValidationRules() {
        Map<SiriDataType, Set<CustomValidator>> rules = new EnumMap<>(SiriDataType.class);
        Map<String, Object> validatorBeans = getContext().getBeansWithAnnotation(Validator.class);

        for (Object o : validatorBeans.values()) {
            if (o instanceof CustomValidator) {
                final String profileName = o.getClass().getAnnotation(Validator.class).profileName();
                if (profileName.equals(configuration.getValidationProfileName())) {
                    final SiriDataType type = o.getClass().getAnnotation(Validator.class).targetType();

                    rules.computeIfAbsent(type, t -> new HashSet<>()).add((CustomValidator) o);
                }
            }
        }
        return rules;
    }


    public Siri parseXml(SubscriptionSetup subscriptionSetup, String xml)
        throws XMLStreamException {
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.getBytes());
        return parseXml(subscriptionSetup, stream);
    }

    public Siri parseXml(SubscriptionSetup subscriptionSetup, InputStream xml)
        throws XMLStreamException {
        try {
            long parseStart = System.currentTimeMillis();

            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            final SiriValidationEventHandler schemaValidationHandler = new SiriValidationEventHandler();

            boolean validate = false;
            if (configuration.isFullValidationEnabled() || 
                                    subscriptionSetup.isValidation()) {
                validate = true;

                // Add schema to validate against
                unmarshaller.setSchema(schema);

                // Add event-handler to collect validation-issues
                unmarshaller.setEventHandler(schemaValidationHandler);

                // Write full stream-contents to disk for easier debugging
                // Files are removed after the configured persist-period
                Files.createDirectories(validationDumpDirectory);
                File targetFile = File.createTempFile(subscriptionSetup.getVendor() + "-", ".xml", validationDumpDirectory.toFile());

                Files.copy(
                        xml,
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
                logger.info("Full contents written to {}", targetFile.getAbsolutePath());

                // Reset stream to continue processing pipeline
                xml.reset();
            }

            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(xml);

            Siri siri = unmarshaller.unmarshal(reader, Siri.class).getValue();

            final String breadcrumbId = MDC.get("camel.breadcrumbId");

            if (siri.getServiceDelivery() != null && validate) {
                validationExecutorService.execute(() -> {
                    MDC.put("subscriptionId", subscriptionSetup.getSubscriptionId());
                    MDC.put("camel.breadcrumbId", breadcrumbId);
                    performProfileValidation(subscriptionSetup, xml, siri, schemaValidationHandler);
                    MDC.remove("camel.breadcrumbId");
                    MDC.remove("subscriptionId");
                });
            }

            long elapsed = System.currentTimeMillis() - parseStart;
            if (elapsed >= 10) {
                // only log when slower than expected
                logger.info("Parsing and validating XML took {} ms ", elapsed);
            }

            return siri;
        } catch (XMLStreamException e) {
            logger.warn("Caught exception when parsing", e);
            throw e;
        } catch (Exception e) {
            logger.warn("Caught exception when parsing", e);
        }
        return null;
    }

    private void addValidationMetrics(SubscriptionSetup subscriptionSetup,
        SiriValidationEventHandler schemaHandler,
        SiriValidationEventHandler profileHandler
    ) {
        final SiriDataType subscriptionType = subscriptionSetup.getSubscriptionType();
        final String codespaceId = subscriptionSetup.getDatasetId();

        schemaHandler.categorizedEvents
            .entrySet()
            .forEach(type -> {
                final Map<String, ValidationEvent> validations = type.getValue();
                for (String key : validations.keySet()) {
                    metricsService.addValidationMetrics(subscriptionType, codespaceId,
                        SCHEMA_VALIDATION, key, schemaHandler.equalsEventCounter.get(key));
                }

            });

        profileHandler.categorizedEvents
            .entrySet()
            .forEach(type -> {
                metricsService.addValidationMetrics(subscriptionType, codespaceId,
                    PROFILE_VALIDATION, type.getKey(),type.getValue().size());
            });

        metricsService.addValidationResult(subscriptionType, codespaceId,
            schemaHandler.categorizedEvents.isEmpty(), profileHandler.categorizedEvents.isEmpty());
    }

    private static AtomicInteger concurrentValidationThreads = new AtomicInteger();
    private boolean performProfileValidation(
        SubscriptionSetup subscriptionSetup, InputStream xml, Siri siri, SiriValidationEventHandler schemaValidationResults
    ) {
        concurrentValidationThreads.incrementAndGet();
        long validationStart = System.currentTimeMillis();

        try {

            /*
             * Setting SIRI-datatype based on subscription
             */
            SiriDataType type = subscriptionSetup.getSubscriptionType();
            if (type == null) {
                return false;
            }

            /*
             * Re-marshalling SIRI-data for filtering-purposes
             */
            xml.reset();

            String originalXml = new String(xml.readAllBytes());

            SiriValidationEventHandler profileValidationResults = new SiriValidationEventHandler();

            validateAttributes(originalXml, type, siri, profileValidationResults);

            if (subscriptionSetup.isValidation()) {
                final long t1 = System.currentTimeMillis();

                if (hasValidationFilter(subscriptionSetup) &&
                    !originalXml.contains(subscriptionSetup.getValidationFilter())) {
                    logger.info("Incoming XML does not contain \"{}\", skip validation-report for this request.",
                        subscriptionSetup.getValidationFilter()
                    );
                    return false;
                }

                JSONObject schemaEvents = schemaValidationResults.toJSON();
                JSONObject profileEvents = profileValidationResults.toJSON();

                JSONObject combinedEvents = new JSONObject();
                combinedEvents.put("schema", schemaEvents);
                combinedEvents.put("profile", profileEvents);

                addResult(subscriptionSetup, originalXml, combinedEvents);

                logger.info("Creating report took: {} ms",
                    (System.currentTimeMillis() - t1)
                );
            }

            addValidationMetrics(subscriptionSetup, schemaValidationResults, profileValidationResults);

            return schemaValidationResults.categorizedEvents.isEmpty() && profileValidationResults.categorizedEvents.isEmpty();

        } catch (Exception e) {
            logger.warn("Caught exception when validating", e);
        } finally {

            long validationDone = System.currentTimeMillis();

            logger.info("Async validation took: {} ms, {} concurrent validations queued",
                validationDone-validationStart,
                concurrentValidationThreads.decrementAndGet());
        }
        return false;
    }

    boolean hasValidationFilter(SubscriptionSetup subscriptionSetup) {
        return subscriptionSetup.getValidationFilter() != null && !subscriptionSetup.getValidationFilter().isEmpty();
    }

    private void validateAttributes(
        String siriXml, SiriDataType type, Siri siri, SiriValidationEventHandler handler
    ) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();

        InputStream stream = new ByteArrayInputStream(siriXml.getBytes(StandardCharsets.UTF_8));

        Document xmlDocument = builder.parse(stream);

        int errorCounter = 0;
        int ruleCounter = 0;
        // No rules may be defined for the type in the current profile
        for (CustomValidator rule : getValidationRules().getOrDefault(type, Set.of())) {
            if (rule instanceof SiriObjectValidator) {
                ValidationEvent event = ((SiriObjectValidator)rule).isValid(siri);
                ruleCounter++;
                if (event != null) {
                    if (event instanceof ProfileValidationEventOrList) {
                        for (ValidationEvent validationEvent : ((ProfileValidationEventOrList) event).getEvents()) {
                            handler.handleCategorizedEvent(rule.getCategoryName(), validationEvent);
                            errorCounter++;
                        }
                    } else {
                        handler.handleCategorizedEvent(rule.getCategoryName(), event);
                        errorCounter++;
                    }
                }
            } else {
                NodeList nodes = (NodeList) xpath.evaluate(rule.getXpath(),
                    xmlDocument,
                    XPathConstants.NODESET
                );
                for (int i = 0; i < nodes.getLength(); i++) {
                    ValidationEvent event = rule.isValid(nodes.item(i));
                    ruleCounter++;
                    if (event != null) {
                        if (event instanceof ProfileValidationEventOrList) {
                            for (ValidationEvent validationEvent : ((ProfileValidationEventOrList) event)
                                .getEvents()) {
                                handler.handleCategorizedEvent(rule.getCategoryName(),
                                    validationEvent
                                );
                                errorCounter++;
                            }
                        }
                        else {
                            handler.handleCategorizedEvent(rule.getCategoryName(), event);
                            errorCounter++;
                        }
                    }
                }
            }
        }
        logger.info("Found {} custom rule violations in SIRI XML, validated {} objects", errorCounter, ruleCounter);
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


    public JSONObject getValidationResults(String internalIdStr) {

        String subscriptionId;
        try {
            Long internalId = Long.valueOf(internalIdStr);
            SubscriptionSetup subscriptionById = subscriptionManager.getSubscriptionById(internalId);

            subscriptionId = subscriptionById.getSubscriptionId();

        } catch (NumberFormatException nfe) {
            subscriptionId = internalIdStr;
        }

        List<String> validationRefs = validationResultRefs.get(subscriptionId);

        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup == null) {
            return null;
        }

        JSONObject validationResult = new JSONObject();

        validationResult.put("subscription", subscriptionSetup.toJSON());

        validationResult.put("status", buildValidationStatus(subscriptionSetup));

        if (validationFilters.containsKey(subscriptionId)) {
            String filter = validationFilters.get(subscriptionId);

            validationResult.put("filter", filter);
        }

        JSONArray resultList = new JSONArray();
        if (validationRefs != null) {
            for (String ref : validationRefs) {
                final JSONObject jsonValidationResults = getJsonValidationResults(ref);
                if (jsonValidationResults != null) {
                    resultList.add(jsonValidationResults);
                }
            }
        }
        validationResult.put("validationRefs", resultList);

        return validationResult;
    }

    private JSONObject buildValidationStatus(SubscriptionSetup subscriptionSetup) {
        int currentValidations = validationResultRefs.getOrDefault(subscriptionSetup.getSubscriptionId(), EMPTY_LIST).size();
        int maxValidations = configuration.getMaxNumberOfValidations();

        long currentSize = validationSize.getOrDefault(subscriptionSetup.getSubscriptionId(), 0L);
        long maxSize = configuration.getMaxTotalXmlSizeOfValidation()*1024*1024;

        JSONObject status = new JSONObject();
        status.put("validationActive", subscriptionSetup.isValidation());
        status.put("currentValidations", currentValidations);
        status.put("maxValidations", maxValidations);
        status.put("currentSize", FileUtils.byteCountToDisplaySize(currentSize));
        status.put("maxSize", FileUtils.byteCountToDisplaySize(maxSize));

        return status;
    }

    private JSONObject getJsonValidationResults(String validationRef) {
        JSONObject jsonObject = validationResults.get(validationRef);
        if (jsonObject == null) {
            return null;
        }
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
                logger.warn("Caught exception when unzipping data", e);
            }
        }
        return null;
    }

    private void addResult(SubscriptionSetup subscriptionSetup, String siriXml, JSONObject jsonObject) throws IOException {

        String newUniqueReference = UUID.randomUUID().toString();

        // GZIP'ing contents to reduce memory-footprint
        byte[] byteArray = compress(siriXml);

        final int persistHours = getPersistHours();
        validatedSiri.set(newUniqueReference, byteArray, persistHours, TimeUnit.HOURS);
        validationResults.set(newUniqueReference, jsonObject, persistHours, TimeUnit.HOURS);

        final List<String> subscriptionValidationRefs;
        final Long totalXmlSize;

        // Locked to avoid concurrent updates overwriting each other
        validationResultRefs.lock(subscriptionSetup.getSubscriptionId());
        try {
            subscriptionValidationRefs = validationResultRefs.getOrDefault(subscriptionSetup.getSubscriptionId(), new ArrayList<>());
            subscriptionValidationRefs.add(newUniqueReference);

            totalXmlSize = (validationSize.getOrDefault(subscriptionSetup.getSubscriptionId(), 0L) + byteArray.length);

            validationResultRefs.set(subscriptionSetup.getSubscriptionId(), subscriptionValidationRefs);
            validationSize.set(subscriptionSetup.getSubscriptionId(), totalXmlSize);
        } finally {
            validationResultRefs.unlock(subscriptionSetup.getSubscriptionId());
        }

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
