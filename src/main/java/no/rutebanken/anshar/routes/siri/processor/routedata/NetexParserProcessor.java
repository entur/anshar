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

import org.entur.netex.NetexParser;
import org.entur.netex.index.api.NetexEntitiesIndex;
import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DayTypeRefStructure;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.JourneyPatternsInFrame_RelStructure;
import org.rutebanken.netex.model.JourneyRefStructure;
import org.rutebanken.netex.model.Journey_VersionStructure;
import org.rutebanken.netex.model.JourneysInFrame_RelStructure;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.OperatingDay;
import org.rutebanken.netex.model.OperatingDayRefStructure;
import org.rutebanken.netex.model.OperatingDaysInFrame_RelStructure;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PointInLinkSequence_VersionedChildStructure;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.ServiceAlterationEnumeration;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.SiteFrame;
import org.rutebanken.netex.model.StopAssignment_VersionStructure;
import org.rutebanken.netex.model.StopAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.TimetabledPassingTime;
import org.rutebanken.netex.model.VehicleModeEnumeration;

import javax.xml.bind.JAXBElement;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public class NetexParserProcessor {

    private Map<String, DayTypeAssignment> dayTypeAssignmentByDayTypeId = new HashMap<>();
    private Map<String, OperatingDay> operatingDaysById = new HashMap<>();
    private Map<String, ServiceJourney> serviceJourneyById = new HashMap<>();
    private Map<String, List<DatedServiceJourney>> datedServiceJourneyForServiceJourneyId = new HashMap<>();
    private Map<String, String> quayIdByStopPointRef = new HashMap<>();
    private Map<String, String> publicCodeByQuayId = new HashMap<>();
    private Map<String, String> journeyPatternIdByServiceJourneyId = new HashMap<>();
    private Map<String, List<PointInLinkSequence_VersionedChildStructure>> pointsInSequenceByJourneyPatternId = new HashMap<>();

    private Map<String, List<StopTime>> tripStops = new HashMap<>();
    private Map<String, Set<String>> trainNumberTrips = new HashMap<>();
    private Map<String, List<ServiceDate>> tripDates = new HashMap<>();
    private static Map<String, String> parentStops = new HashMap<>();
    private Map<String, LocationStructure> locations = new HashMap<>();
    private Map<String, VehicleModeEnumeration> modes = new HashMap<>();

    public Map<String, List<StopTime>> getTripStops() {
        return tripStops;
    }

    public Map<String, Set<String>> getTrainNumberTrips() {
        return trainNumberTrips;
    }

    public Map<String, List<DatedServiceJourney>> getDatedServiceJourneyForServiceJourneyId() {
        return datedServiceJourneyForServiceJourneyId;
    }
    public Map<String, OperatingDay> getOperatingDayRefs() {
        return operatingDaysById;
    }

    public Map<String, List<ServiceDate>> getTripDates() {
        return tripDates;
    }

    public Map<String, String> getParentStops() {
        return parentStops;
    }

    public Map<String, String> getPublicCodeByQuayId() {
        return publicCodeByQuayId;
    }

    public Map<String, LocationStructure> getLocations() {
        return locations;
    }

    public Map<String, VehicleModeEnumeration> getModes() {
        return modes;
    }

    public void loadFiles(File file) throws IOException {
        NetexEntitiesIndex entitiesIndex = new NetexParser().parse(file.getAbsolutePath());

        loadFile(entitiesIndex);
        populateTrips();
    }

    private void populateTrips() {
        for (ServiceJourney serviceJourney : serviceJourneyById.values()) {
            String serviceJourneyId = serviceJourney.getId();
            if (serviceJourney.getDayTypes() != null) {
                ArrayList<ServiceDate> dates = new ArrayList<>();
                List<JAXBElement<? extends DayTypeRefStructure>> dayTypeRefs = serviceJourney.getDayTypes().getDayTypeRef();
                for (JAXBElement<? extends DayTypeRefStructure> dayTypeRef : dayTypeRefs) {
                    DayTypeAssignment dayTypeAssignment = dayTypeAssignmentByDayTypeId.get(dayTypeRef.getValue().getRef());
                    if (dayTypeAssignment != null) {
                        LocalDateTime date = dayTypeAssignment.getDate();
                        final ServiceDate d = new ServiceDate(date.getYear(),
                                date.getMonthValue(),
                                date.getDayOfMonth()
                        );
                        dates.add(d);
                    }
                }
                tripDates.put(serviceJourneyId, dates);
            } else if (datedServiceJourneyForServiceJourneyId.containsKey(serviceJourneyId)) {
                List<DatedServiceJourney> datedServiceJourneys = datedServiceJourneyForServiceJourneyId.get(serviceJourneyId);
                ArrayList<ServiceDate> dates = new ArrayList<>();
                for (DatedServiceJourney dsj : datedServiceJourneys) {
                    OperatingDayRefStructure operatingDayRef = dsj.getOperatingDayRef();
                    OperatingDay operatingDay = operatingDaysById.get(operatingDayRef.getRef());
                    if (operatingDay != null) {
                        LocalDateTime date = operatingDay.getCalendarDate();
                        final ServiceDate d = new ServiceDate(date.getYear(),
                                date.getMonthValue(),
                                date.getDayOfMonth()
                        );
                        dates.add(d);
                    }
                }
                tripDates.put(serviceJourneyId, dates);
            }
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
    private void loadFile(NetexEntitiesIndex entitiesIndex) {

        for (CompositeFrame cf : entitiesIndex.getCompositeFrames()) {
            List<JAXBElement<? extends Common_VersionFrameStructure>> commonFrames = cf.getFrames().getCommonFrame();
            for (JAXBElement commonFrame : commonFrames) {
                loadServiceFrames(commonFrame);
                loadServiceCalendarFrames(commonFrame);
                loadTimeTableFrames(commonFrame);
            }
        }
        for (SiteFrame siteFrame : entitiesIndex.getSiteFrames()) {
            loadSiteFrames(siteFrame);
        }
    }

    private void loadTimeTableFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof TimetableFrame) {
            TimetableFrame timetableFrame = (TimetableFrame) commonFrame.getValue();
            JourneysInFrame_RelStructure vehicleJourneys = timetableFrame.getVehicleJourneys();
            List<Journey_VersionStructure> datedServiceJourneyOrDeadRunOrServiceJourney = vehicleJourneys.getVehicleJourneyOrDatedVehicleJourneyOrNormalDatedVehicleJourney();
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
                } else if (jStructure instanceof DatedServiceJourney) {
                    DatedServiceJourney dsj = (DatedServiceJourney) jStructure;

                    Optional<JAXBElement<? extends JourneyRefStructure>> first = dsj.getJourneyRef().stream().findFirst();
                    if (first.isPresent()) {
                        JourneyRefStructure journeyRefStructure = first.get().getValue();
                        String serviceJourneyRef = journeyRefStructure.getRef();

                        List<DatedServiceJourney> datedServiceJourneys = datedServiceJourneyForServiceJourneyId.getOrDefault(serviceJourneyRef, new ArrayList<>());
                        datedServiceJourneys.add(dsj);

                        datedServiceJourneyForServiceJourneyId.put(serviceJourneyRef, datedServiceJourneys);
                    }
                }
            }
        }
    }

    private void loadServiceCalendarFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof ServiceCalendarFrame) {
            ServiceCalendarFrame scf = (ServiceCalendarFrame) commonFrame.getValue();
            if (scf.getDayTypeAssignments() != null) {
                List<DayTypeAssignment> dayTypeAssignments = scf.getDayTypeAssignments().getDayTypeAssignment();
                for (DayTypeAssignment dayTypeAssignment : dayTypeAssignments) {
                    String ref = dayTypeAssignment.getDayTypeRef().getValue().getRef();
                    dayTypeAssignmentByDayTypeId.put(ref, dayTypeAssignment);
                }
            } else if (scf.getOperatingDays() != null) {
                OperatingDaysInFrame_RelStructure operatingDaysInFrame = scf.getOperatingDays();
                if (operatingDaysInFrame != null && operatingDaysInFrame.getOperatingDay() != null) {
                    List<OperatingDay> operatingDays = operatingDaysInFrame.getOperatingDay();
                    for (OperatingDay operatingDay : operatingDays) {
                        String id = operatingDay.getId();
                        operatingDaysById.put(id, operatingDay);
                    }
                }
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

    private void loadSiteFrames(SiteFrame sf) {
        if (sf.getStopPlaces() != null) {
            List<StopPlace> stopPlaces = sf.getStopPlaces().getStopPlace();
            for (StopPlace stopPlace : stopPlaces) {
                String parentId = stopPlace.getId();

                if (stopPlace.getCentroid() != null && stopPlace.getCentroid().getLocation() != null) {
                    locations.put(stopPlace.getId(), stopPlace.getCentroid().getLocation());
                }
                VehicleModeEnumeration mode = null;
                if (stopPlace.getTransportMode() != null) {
                    mode = stopPlace.getTransportMode();
                    modes.put(stopPlace.getId(), mode);
                }

                if (stopPlace.getQuays() != null) {
                    List<Object> quayRefOrQuay = stopPlace.getQuays().getQuayRefOrQuay();
                    for (Object o : quayRefOrQuay) {
                        if (o instanceof Quay) {
                            Quay quay = (Quay) o;
                            parentStops.put(quay.getId(), parentId);
                            modes.put(quay.getId(), mode);

                            if (quay.getPublicCode() != null && !quay.getPublicCode().isEmpty()) {
                                publicCodeByQuayId.put(quay.getId(), quay.getPublicCode());
                            }

                            if (quay.getCentroid() != null && quay.getCentroid().getLocation() != null) {
                                locations.put(quay.getId(), quay.getCentroid().getLocation());
                            } else {
                                System.err.println();
                            }
                        }
                    }
                }
            }
        }
    }
}
