/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *  https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri.processor.routedata;

import org.apache.commons.io.IOUtils;
import org.rutebanken.netex.model.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("WeakerAccess")
public class NetexProcessor {

    private static JAXBContext jaxbContext;

    private Map<String, DayTypeAssignment> dayTypeAssignmentByDayTypeId = new HashMap<>();
    private Map<String, ServiceJourney> serviceJourneyById = new HashMap<>();
    private Map<String, String> quayIdByStopPointRef = new HashMap<>();
    private Map<String, String> journeyPatternIdByServiceJourneyId = new HashMap<>();
    private Map<String, List<PointInLinkSequence_VersionedChildStructure>> pointsInSequenceByJourneyPatternId = new HashMap<>();

    private Map<String, List<StopTime>> tripStops = new HashMap<>();
    private Map<String, Set<String>> trainNumberTrips = new HashMap<>();
    private Map<String, List<ServiceDate>> tripDates = new HashMap<>();
    private static Map<String, String> parentStops = new HashMap<>();

    static {
        try {
            jaxbContext = JAXBContext.newInstance(PublicationDeliveryStructure.class);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    public Map<String, List<StopTime>> getTripStops() {
        return tripStops;
    }

    public Map<String, Set<String>> getTrainNumberTrips() {
        return trainNumberTrips;
    }

    public Map<String, List<ServiceDate>> getTripDates() {
        return tripDates;
    }

    public Map<String, String> getParentStops() {
        return parentStops;
    }

    private Unmarshaller createUnmarshaller() throws JAXBException {
        return jaxbContext.createUnmarshaller();
    }

    public void loadFiles(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ);
        zipFile.stream().forEach(entry -> loadFile(entry, zipFile));
        populateTrips();
    }

    private void populateTrips() {
        for (ServiceJourney serviceJourney : serviceJourneyById.values()) {
            String serviceJourneyId = serviceJourney.getId();
            ArrayList<ServiceDate> dates = new ArrayList<>();
            List<JAXBElement<? extends DayTypeRefStructure>> dayTypeRefs = serviceJourney.getDayTypes().getDayTypeRef();
            for (JAXBElement<? extends DayTypeRefStructure> dayTypeRef : dayTypeRefs) {
                DayTypeAssignment dayTypeAssignment = dayTypeAssignmentByDayTypeId.get(dayTypeRef.getValue().getRef());
                LocalDateTime date = dayTypeAssignment.getDate();
                dates.add(new ServiceDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
            }
            tripDates.put(serviceJourneyId, dates);
        }

        for (Map.Entry<String, String> serviceJourneyIdAndJourneyPatternId : journeyPatternIdByServiceJourneyId.entrySet()) {
            String journeyPatternId = serviceJourneyIdAndJourneyPatternId.getValue();
            List<PointInLinkSequence_VersionedChildStructure> pointsInSequence = pointsInSequenceByJourneyPatternId.get(journeyPatternId);
            String serviceJourneyId = serviceJourneyIdAndJourneyPatternId.getKey();
            ServiceJourney serviceJourney = serviceJourneyById.get(serviceJourneyId);
            ArrayList<StopTime> stopTimes = new ArrayList<>();
            List<TimetabledPassingTime> passingTimes = serviceJourney.getPassingTimes().getTimetabledPassingTime();
            for (PointInLinkSequence_VersionedChildStructure point : pointsInSequence) {
                int order = point.getOrder().intValue() - 1; //We want order to start on 0, not 1 as they here
                String stopPointRef = ((StopPointInJourneyPattern)point).getScheduledStopPointRef().getValue().getRef();
                String quay = quayIdByStopPointRef.get(stopPointRef);
                TimetabledPassingTime timetabledPassingTime = passingTimes.get(order);
                int arrivalTime = getTimeInSecondsOfDay(timetabledPassingTime.getArrivalTime(), timetabledPassingTime.getArrivalDayOffset());
                int departureTime = getTimeInSecondsOfDay(timetabledPassingTime.getDepartureTime(), timetabledPassingTime.getDepartureDayOffset());
                //TODO: The if-statement below is there to give same result as the original gtfs service, remove it if it's not needed any more
                if (arrivalTime == 0) {
                    arrivalTime = departureTime;
                } else if (departureTime == 0) {
                    departureTime = arrivalTime;
                }
                stopTimes.add(new StopTime(quay, order, arrivalTime, departureTime));
            }
            tripStops.put(serviceJourneyId, stopTimes);
        }

    }

    private int getTimeInSecondsOfDay(LocalTime time, BigInteger dayOffset) {
        if (time == null) return 0;
        int seconds = time.toSecondOfDay();
        if (dayOffset != null) {
            seconds += dayOffset.intValue() * 86400; //24x60x60
        }
        return seconds;
    }

    private byte[] entryAsBytes(ZipFile zipFile, ZipEntry entry) {
        try {
            return IOUtils.toByteArray(zipFile.getInputStream(entry));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void loadFile(ZipEntry entry, ZipFile zipFile) {
        try {
            byte[] bytesArray = entryAsBytes(zipFile, entry);
            PublicationDeliveryStructure value = parseXmlDoc(bytesArray);
            List<JAXBElement<? extends Common_VersionFrameStructure>> compositeFrameOrCommonFrames = value.getDataObjects().getCompositeFrameOrCommonFrame();
            for (JAXBElement frame : compositeFrameOrCommonFrames) {
                if (frame.getValue() instanceof CompositeFrame) {
                    CompositeFrame cf = (CompositeFrame) frame.getValue();
                    List<JAXBElement<? extends Common_VersionFrameStructure>> commonFrames = cf.getFrames().getCommonFrame();
                    for (JAXBElement commonFrame : commonFrames) {
                        loadServiceFrames(commonFrame);
                        loadServiceCalendarFrames(commonFrame);
                        loadTimeTableFrames(commonFrame);
                    }
                } else {
                    loadSiteFrames(frame);
                }
            }
        } catch (JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private PublicationDeliveryStructure parseXmlDoc(byte[] bytesArray) throws JAXBException {
        JAXBElement<PublicationDeliveryStructure> root;
        ByteArrayInputStream stream = new ByteArrayInputStream(bytesArray);
        //noinspection unchecked
        root = (JAXBElement<PublicationDeliveryStructure>) createUnmarshaller().unmarshal(stream);
        return root.getValue();
    }

    private void loadTimeTableFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof TimetableFrame) {
            TimetableFrame timetableFrame = (TimetableFrame) commonFrame.getValue();
            JourneysInFrame_RelStructure vehicleJourneys = timetableFrame.getVehicleJourneys();
            List<Journey_VersionStructure> datedServiceJourneyOrDeadRunOrServiceJourney = vehicleJourneys.getDatedServiceJourneyOrDeadRunOrServiceJourney();
            for (Journey_VersionStructure jStructure : datedServiceJourneyOrDeadRunOrServiceJourney) {
                if (jStructure instanceof ServiceJourney) {
                    ServiceJourney sj = (ServiceJourney) jStructure;

                    //Only process RAIL-mode
                    if (AllVehicleModesOfTransportEnumeration.RAIL.equals(sj.getTransportMode())) {
                        if (sj.getPrivateCode() != null) {
                            String trainNumber = sj.getPrivateCode().getValue();
                            if (sj.getServiceAlteration() != null && sj.getServiceAlteration() == ServiceAlterationEnumeration.CANCELLATION) {
                                //Ignore planned cancellations
                            } else {
                                Set<String> trips = trainNumberTrips.getOrDefault(trainNumber, new HashSet<>());
                                trips.add(sj.getId());
                                trainNumberTrips.put(trainNumber, trips);
                            }
                        }
                        if (sj.getJourneyPatternRef() != null && sj.getJourneyPatternRef().getValue() != null) {
                            journeyPatternIdByServiceJourneyId.put(sj.getId(), sj.getJourneyPatternRef().getValue().getRef());
                        }
                        serviceJourneyById.put(sj.getId(), sj);
                    }
                }
            }
        }
    }

    private void loadServiceCalendarFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof ServiceCalendarFrame) {
            ServiceCalendarFrame scf = (ServiceCalendarFrame) commonFrame.getValue();
            List<DayTypeAssignment> dayTypeAssignments = scf.getDayTypeAssignments().getDayTypeAssignment();
            for (DayTypeAssignment dayTypeAssignment : dayTypeAssignments) {
                String ref = dayTypeAssignment.getDayTypeRef().getValue().getRef();
                dayTypeAssignmentByDayTypeId.put(ref, dayTypeAssignment);
            }
        }
    }

    private void loadServiceFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof ServiceFrame) {
            ServiceFrame sf = (ServiceFrame) commonFrame.getValue();

            //stop assignments
            StopAssignmentsInFrame_RelStructure stopAssignments = sf.getStopAssignments();
            if (stopAssignments != null) {
                List<JAXBElement<? extends StopAssignment_VersionStructure>> assignments = stopAssignments.getStopAssignment();
                for (JAXBElement assignment : assignments) {
                    if (assignment.getValue() instanceof PassengerStopAssignment) {
                        PassengerStopAssignment passengerStopAssignment =  (PassengerStopAssignment) assignment.getValue();
                        String quayRef = passengerStopAssignment.getQuayRef().getRef();
                        quayIdByStopPointRef.put(passengerStopAssignment.getScheduledStopPointRef().getValue().getRef(), quayRef);
                    }
                }
            }

            //journeyPatterns
            JourneyPatternsInFrame_RelStructure journeyPatterns = sf.getJourneyPatterns();
            if (journeyPatterns != null) {
                List<JAXBElement<?>> journeyPattern_orJourneyPatternView = journeyPatterns.getJourneyPattern_OrJourneyPatternView();
                for (JAXBElement pattern : journeyPattern_orJourneyPatternView) {
                    if (pattern.getValue() instanceof JourneyPattern) {
                        JourneyPattern journeyPattern = (JourneyPattern) pattern.getValue();
                        if (journeyPattern.getPointsInSequence() != null) {
                            List<PointInLinkSequence_VersionedChildStructure> pointsInSequence = journeyPattern.getPointsInSequence().getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();
                            pointsInSequenceByJourneyPatternId.put(journeyPattern.getId(), pointsInSequence);
                        }
                    }
                }

            }
        }
    }

    private void loadSiteFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof SiteFrame) {
            SiteFrame sf = (SiteFrame) commonFrame.getValue();
            if (sf.getStopPlaces() != null) {
                List<StopPlace> stopPlaces = sf.getStopPlaces().getStopPlace();
                for (StopPlace stopPlace : stopPlaces) {
                    String parentId = stopPlace.getId();
                    if (stopPlace.getQuays() != null) {
                        List<Object> quayRefOrQuay = stopPlace.getQuays().getQuayRefOrQuay();
                        for (Object o : quayRefOrQuay) {
                            if (o instanceof Quay) {
                                parentStops.put(((Quay)o).getId(), parentId);
                            }
                        }
                    }
                }
            }
        }
    }
}
