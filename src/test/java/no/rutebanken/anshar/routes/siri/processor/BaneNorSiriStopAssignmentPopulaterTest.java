package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.mapping.BaneNorIdPlatformUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceRegisterMappingFetcher;
import no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopAssignmentStructure;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class BaneNorSiriStopAssignmentPopulaterTest {

    private static final Logger logger = LoggerFactory.getLogger(BaneNorSiriStopAssignmentPopulaterTest.class);

    @Test
    public void testStopAssignmentPopulation() throws Exception {
        //Important to use these files recorded while we produced the test siri file so ids match!
//        NSBGtfsUpdaterService.update("src/test/resources/rb_nsb-aggregated-gtfs.zip",
//                "src/test/resources/rb_gjb-aggregated-gtfs.zip",
//                "src/test/resources/rb_flt-aggregated-gtfs.zip");
        NetexUpdaterService.update("src/test/resources/rb_nsb-aggregated-netex.zip",
                "src/test/resources/rb_gjb-aggregated-netex.zip",
                "src/test/resources/rb_flt-aggregated-netex.zip",
                "src/test/resources/CurrentAndFuture_latest.zip");

        long start = System.currentTimeMillis();
        StopPlaceRegisterMappingFetcher mappingFetcher = new StopPlaceRegisterMappingFetcher();
        Map<String, String> stopPlaceMapping = mappingFetcher.fetchStopPlaceMapping(new File("src/test/resources/jbv_code_mapping.csv").toURI().toString());
        logger.info("Got {} stopplace mappings in {} ms", stopPlaceMapping.size(), (System.currentTimeMillis()-start));
        BaneNorIdPlatformUpdaterService platformUpdaterService = Mockito.mock(BaneNorIdPlatformUpdaterService.class);
        Mockito.when(platformUpdaterService.get(Mockito.anyString())).thenAnswer((Answer<String>) invocation -> stopPlaceMapping.get(invocation.getArguments()[0]));
        ApplicationContextHolder applicationContextHolder = new ApplicationContextHolder();
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        applicationContextHolder.setApplicationContext(applicationContext);
        Mockito.when(applicationContext.getBean(BaneNorIdPlatformUpdaterService.class)).thenReturn(platformUpdaterService);
        Mockito.when(applicationContext.getBean(HealthManager.class)).thenReturn(Mockito.mock(HealthManager.class));

        Siri siri = unmarshallSiriFile("src/test/resources/raw_et_banenor.xml");

        BaneNorIdPlatformPostProcessor platformPostProcessor = new BaneNorIdPlatformPostProcessor(SiriDataType.ESTIMATED_TIMETABLE, "BNR");
        platformPostProcessor.process(siri);

        BaneNorSiriEtRewriter siriEtRewriter = new BaneNorSiriEtRewriter("BNR");
        siriEtRewriter.process(siri);

//        Siri siri = unmarshallSiriFile("src/test/resources/siriAfterBaneNorSiriEtRewriting.xml");
        BaneNorSiriStopAssignmentPopulater populater = new BaneNorSiriStopAssignmentPopulater("BNR");
        populater.process(siri);

        //Checks resulting Siri for track-changes and cases where we can't populate stopAssignments
        int foundJourneys = 0;
        int notFoundJourneys = 0;
        HashMap<String, Integer> operators = new HashMap<>();
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            String operator = estimatedVehicleJourney.getOperatorRef().getValue();
                            Integer count = operators.get(operator);
                            if (count == null) {
                                count = 0;
                            }
                            operators.put(operator, ++count);
                            String datedVehicleJourney = estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue();
                            if (StringUtils.startsWithAny(datedVehicleJourney, "NSB", "GJB", "FLT")) {
                                foundJourneys++;
                                ArrayList<Integer> trackChanges = new ArrayList<>();
                                boolean noStopAssignment = false;
                                for (EstimatedCall estimatedCall : estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls()) {
                                    int order = estimatedCall.getOrder().intValue();
                                    StopAssignmentStructure stopAssignment = (order>1) ? estimatedCall.getArrivalStopAssignments().get(0) : estimatedCall.getDepartureStopAssignments().get(0);
                                    if (stopAssignment == null) {
                                        noStopAssignment = true;
                                    } else {
                                        String aimed = stopAssignment.getAimedQuayRef().getValue();
                                        String expected = stopAssignment.getExpectedQuayRef().getValue();
                                        if (!aimed.equals(expected)) {
                                            trackChanges.add(order);
                                        }
                                    }
                                }
                                if (noStopAssignment) {
                                    logger.warn("There are no stopAssignments for datedVehicleJourney {}", datedVehicleJourney);
                                }
                                if (!trackChanges.isEmpty()) {
                                    logger.debug("Calls with track change for datedVehicleJourney {}:\t{}", datedVehicleJourney, trackChanges);
                                }
                            } else {
                                notFoundJourneys++;
                            }
                        }
                    }
                }
            }
        }

        logger.info("These operators are present (name: count):");
        for (Map.Entry<String, Integer> entry : operators.entrySet()) {
            String extrainfo = "";
            if ("FLY".equals(entry.getKey())) {
                extrainfo = "<- FLT";
            } else if ("NG".equals(entry.getKey())) {
                extrainfo = "<- GJB";
            }
            logger.info("  {}:\t {}  {}", entry.getKey(), entry.getValue(), extrainfo);
        }
        logger.info("There are {} journeys mapped from route data, and {} that are not", foundJourneys, notFoundJourneys);
        assertEquals(1030, foundJourneys);
        String filename = "/tmp/BaneNorSiri_"+System.currentTimeMillis()+".xml";
        logger.info("Writes resulting XML to file: {}", filename);
        marshallToFile(siri, filename);
    }


    @SuppressWarnings("SameParameterValue")
    static Siri unmarshallSiriFile(String filename) throws JAXBException, XMLStreamException, FileNotFoundException {
        JAXBContext jaxbContext = JAXBContext.newInstance(Siri.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        FileInputStream xml = new FileInputStream(filename);
        XMLStreamReader xmlsr = xmlif.createXMLStreamReader(xml);
        return (Siri) jaxbUnmarshaller.unmarshal(xmlsr);
    }

    private void marshallToFile(Siri siri, String filename) throws JAXBException, IOException {
        JAXBContext jaxbContext = JAXBContext.newInstance(Siri.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        FileWriter writer = new FileWriter(filename);
        jaxbMarshaller.marshal(siri, writer);
        writer.close();
    }

}