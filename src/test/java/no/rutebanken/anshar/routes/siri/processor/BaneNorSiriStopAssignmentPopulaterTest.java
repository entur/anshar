package no.rutebanken.anshar.routes.siri.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BaneNorSiriStopAssignmentPopulaterTest {

    private static final Logger logger = LoggerFactory.getLogger(BaneNorSiriStopAssignmentPopulaterTest.class);

    @Test
    public void testStopAssignmentPopulation() throws Exception {
        //Important to use these gtfs files recorded while we produced the test siri file so ids match!
        NSBGtfsUpdaterService.update("src/test/resources/rb_nsb-aggregated-gtfs.zip",
                "src/test/resources/rb_gjb-aggregated-gtfs.zip",
                "src/test/resources/rb_flt-aggregated-gtfs.zip");

        Siri siri = unmarshallSiriFile("src/test/resources/siriAfterBaneNorSiriEtRewriting.xml");
        BaneNorSiriStopAssignmentPopulater populater = new BaneNorSiriStopAssignmentPopulater();
        populater.process(siri);

        //Checks resulting Siri for track-changes and cases where we can't populate stopAssignments
        int foundJourneys = 0;
        int notFoundJourneys = 0;
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            String datedVehicleJourney = estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue();
                            if (StringUtils.startsWithAny(datedVehicleJourney, "NSB", "GJB", "FLT")) {
                                foundJourneys++;
                                ArrayList<Integer> trackChanges = new ArrayList<>();
                                boolean noStopAssignment = false;
                                for (EstimatedCall estimatedCall : estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls()) {
                                    int order = estimatedCall.getOrder().intValue();
                                    StopAssignmentStructure stopAssignment = (order>1) ? estimatedCall.getArrivalStopAssignment() : estimatedCall.getDepartureStopAssignment();
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

        assertEquals(1031, foundJourneys);
        logger.info("There are {} journeys mapped from route data, and {} that are not", foundJourneys, notFoundJourneys);
        String filename = "/tmp/BaneNorSiri_"+System.currentTimeMillis()+".xml";
        logger.info("Writes resulting XML to file: {}", filename);
        marshallToFile(siri, filename);
    }

    @Test
    @Ignore //This is a manual test to make sure the static NSBGtfsUpdaterService works after some refactoring related to the BaneNorSiriStopAssignmentPopulater
    public void testGtfsUpdater() throws InterruptedException {
        LoggerContext logCtx = (LoggerContext) LoggerFactory.getILoggerFactory();
        logCtx.getLogger("no.rutebanken.anshar.routes.siri.processor").setLevel(Level.DEBUG);
        //temporary adjusting the FREQUENCY-variables might be an idea before running the test...
        NSBGtfsUpdaterService.initializeUpdater();
        Thread.sleep(3_600_000);
    }

    @SuppressWarnings("SameParameterValue")
    private Siri unmarshallSiriFile(String filename) throws JAXBException, XMLStreamException, FileNotFoundException {
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