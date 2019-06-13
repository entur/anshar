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

package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.processor.routedata.ServiceDate;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopTime;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.*;
import static no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer.SEPARATOR;

/**
 * Rewrites the SIRI ET-feed from BaneNOR to match the planned routes received from NSB
 *
 */
public class BaneNorSiriEtRewriter extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BaneNorSiriEtRewriter.class);

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        long startTime = System.currentTimeMillis();
        int previousSize = 0;
        int newSize = 0;

        if (siri != null && siri.getServiceDelivery() != null) {

            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {

                        List<EstimatedVehicleJourney> restructuredDeliveryContent = new ArrayList<>();

                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                        previousSize += estimatedVehicleJourneies.size();

                        Map<String, List<EstimatedVehicleJourney>> restructuredJourneyList = new HashMap<>();
                        Map<String, List<EstimatedVehicleJourney>> extraJourneyList = new HashMap<>();

                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {

                            if (estimatedVehicleJourney.getVehicleRef() != null) {
                                String etTrainNumber = estimatedVehicleJourney.getVehicleRef().getValue();

                                if (isKnownTrainNr(etTrainNumber )) {

                                    boolean foundMatch = false;
                                    ZonedDateTime firstDepartureTime = getFirstDepartureTime(estimatedVehicleJourney);
                                    ServiceDate serviceDate = new ServiceDate(firstDepartureTime);

                                    if (estimatedVehicleJourney.isExtraJourney() != null && estimatedVehicleJourney.isExtraJourney()) {
                                        //Extra journey - ignore comparison to planned data
                                        foundMatch = true;
                                    } else {
                                        Set<String> serviceJourneys = getServiceJourney(etTrainNumber);
                                        for (String serviceJourney : serviceJourneys) {
                                            List<ServiceDate> serviceDates = getServiceDates(serviceJourney);
                                            if (serviceDates.contains(serviceDate)) {
                                                foundMatch = true;
                                                break;
                                            }
                                        }
                                    }


                                    if (foundMatch) {
                                        restructuredJourneyList.put(etTrainNumber, reStructureEstimatedJourney(estimatedVehicleJourney, etTrainNumber));
                                    } else {
                                        logger.warn("Ignoring realtime-data for departure not found in NeTEx for given day - train number {}, {}", etTrainNumber, serviceDate);
                                    }

                                }  else if (etTrainNumber.length() == 5 && (etTrainNumber.startsWith("905") | etTrainNumber.startsWith("908"))) {
                                    //Extra journey - map EstimatedCall as the original train
                                    String extraTrainOriginalTrainNumber = etTrainNumber.substring(2);

                                    logger.warn("Found added train {} to be merged with original {}", etTrainNumber, extraTrainOriginalTrainNumber);

                                    if (isKnownTrainNr(extraTrainOriginalTrainNumber)) {
                                        // Restructure the extra train as if it was the original
                                        extraJourneyList.put(extraTrainOriginalTrainNumber, reStructureEstimatedJourney(estimatedVehicleJourney, extraTrainOriginalTrainNumber));
                                    }
                                } else {
                                    // Match not found - keep data unchanged
                                    restructuredJourneyList.put(etTrainNumber, Collections.singletonList(estimatedVehicleJourney));
                                }
                            }


                        }
                        for (String originalTrainNumber : restructuredJourneyList.keySet()) {
                            if (extraJourneyList.containsKey(originalTrainNumber)) {

                                List<EstimatedVehicleJourney> extraVehicleJourneys = extraJourneyList.get(originalTrainNumber);
                                List<EstimatedVehicleJourney> originalVehicleJourneys = restructuredJourneyList.get(originalTrainNumber);

                                if (extraVehicleJourneys.size() == 2 && originalVehicleJourneys.size() == 2) {

                                    sortByFirstAimedDeparture(extraVehicleJourneys);
                                    sortByFirstAimedDeparture(originalVehicleJourneys);

                                    // Add first part from extra-train
                                    EstimatedVehicleJourney extraVehicleJourney = extraVehicleJourneys.get(0);
                                    extraVehicleJourney.getVehicleRef().setValue(originalTrainNumber);
                                    restructuredDeliveryContent.add(extraVehicleJourney);

                                    // ...and last part from original
                                    EstimatedVehicleJourney originalVehicleJourney = originalVehicleJourneys.get(1);
                                    restructuredDeliveryContent.add(originalVehicleJourney);

                                    logger.warn("Keeping serviceJourney {} from added train, {} from original.",
                                            extraVehicleJourney.getDatedVehicleJourneyRef().getValue(),
                                            originalVehicleJourney.getDatedVehicleJourneyRef().getValue());

                                } else {
                                    restructuredDeliveryContent.addAll(originalVehicleJourneys);
                                }


                            } else {
                                restructuredDeliveryContent.addAll(restructuredJourneyList.get(originalTrainNumber));
                            }
                        }
                        estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().clear();
                        estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().addAll(restructuredDeliveryContent);

                        newSize += restructuredDeliveryContent.size();
                    }
                }
            }
        }
        logger.info("Restructured SIRI ET from {} to {} journeys in {} ms", previousSize, newSize, (System.currentTimeMillis()-startTime));
    }

    private void sortByFirstAimedDeparture(List<EstimatedVehicleJourney> journeys) {
        journeys.sort(Comparator.comparing(e -> {
                if (e.getRecordedCalls() != null && !e.getRecordedCalls().getRecordedCalls().isEmpty()) {
                    return e.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
                }
                return e.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
            })
        );
    }

    private List<EstimatedVehicleJourney> reStructureEstimatedJourney(EstimatedVehicleJourney estimatedVehicleJourney, String etTrainNumber) {
        List<EstimatedVehicleJourney> restructuredJourneyList = new ArrayList<>();
        ZonedDateTime departureTime = getFirstDepartureTime(estimatedVehicleJourney);

        Map<String, List<EstimatedCall>> remappedEstimatedCalls = new HashMap<>();
        Map<String, List<RecordedCall>> recordedTrip = new HashMap<>();
        if (departureTime != null) {
            ServiceDate serviceDate = new ServiceDate(departureTime);
            recordedTrip.putAll(remapRecordedCalls(serviceDate, etTrainNumber, estimatedVehicleJourney.getRecordedCalls()));
            remappedEstimatedCalls.putAll(remapEstimatedCalls(serviceDate, etTrainNumber, estimatedVehicleJourney.getEstimatedCalls()));
        }
        if (remappedEstimatedCalls.size() < 1) {
            restructuredJourneyList.add(estimatedVehicleJourney);
        } else {

            for (String id : remappedEstimatedCalls.keySet()) {
                List<RecordedCall> recordedCalls = recordedTrip.get(id);
                List<EstimatedCall> estimatedCalls = remappedEstimatedCalls.get(id);

                String departureDate;
                if (departureTime != null) {
                    departureDate = departureTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
                } else {
                    departureDate = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                }

                int order = 1;
                if (recordedCalls != null) {
                    for (RecordedCall recordedCall : recordedCalls) {
                        recordedCall.setOrder(BigInteger.valueOf(order++));
                    }
                }
                for (EstimatedCall estimatedCall : estimatedCalls) {
                    estimatedCall.setOrder(BigInteger.valueOf(order++));
                }

                EstimatedVehicleJourney journey = new EstimatedVehicleJourney();

                journey.setLineRef(estimatedVehicleJourney.getLineRef());
                journey.setDirectionRef(estimatedVehicleJourney.getDirectionRef());
                journey.setDatedVehicleJourneyRef(estimatedVehicleJourney.getDatedVehicleJourneyRef());

                journey.getVehicleModes().addAll(estimatedVehicleJourney.getVehicleModes());
                journey.setOperatorRef(estimatedVehicleJourney.getOperatorRef());
                journey.getServiceFeatureReves().addAll(estimatedVehicleJourney.getServiceFeatureReves());
                journey.setVehicleRef(estimatedVehicleJourney.getVehicleRef());

                journey.setIsCompleteStopSequence(estimatedVehicleJourney.isIsCompleteStopSequence());
                journey.setCancellation(estimatedVehicleJourney.isCancellation());

                if (recordedCalls != null) {
                    EstimatedVehicleJourney.RecordedCalls restructuredRecordedCalls = new EstimatedVehicleJourney.RecordedCalls();
                    restructuredRecordedCalls.getRecordedCalls().addAll(recordedCalls);
                    journey.setRecordedCalls(restructuredRecordedCalls);
                }

                EstimatedVehicleJourney.EstimatedCalls restructuredCalls = new EstimatedVehicleJourney.EstimatedCalls();
                restructuredCalls.getEstimatedCalls().addAll(estimatedCalls);
                journey.setEstimatedCalls(restructuredCalls);

                restructuredJourneyList.add(journey);
            }
        }
        return restructuredJourneyList;
    }

    private ZonedDateTime getFirstDepartureTime(EstimatedVehicleJourney estimatedVehicleJourney) {
        ZonedDateTime departureTime;
        if (estimatedVehicleJourney.getRecordedCalls() != null && !estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
            departureTime = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
        } else {
            departureTime = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
        }
        return departureTime;
    }

    private Map<String, List<EstimatedCall>> remapEstimatedCalls(ServiceDate serviceDate, String etTrainNumber, EstimatedVehicleJourney.EstimatedCalls estimatedCallsWrapper) {

        Map<String, List<EstimatedCall>> matches = new HashMap<>();

        if (estimatedCallsWrapper == null) {
            return matches;
        }
        List<EstimatedCall> estimatedCalls = estimatedCallsWrapper.getEstimatedCalls();

        Set<String> serviceJourneyIds = getServiceJourney(etTrainNumber);

        for (String serviceJourneyId : serviceJourneyIds) {
            List<StopTime> stopTimes = getStopTimes(serviceJourneyId);

            // Override known unmappable stops
            if (estimatedCalls.size() == stopTimes.size()) {
                for (int i = 0; i < estimatedCalls.size(); i++) {
                    EstimatedCall call = estimatedCalls.get(i);
                    StopPointRef stopPointRef = call.getStopPointRef();

                    if (call.getArrivalPlatformName() == null &&
                            call.getDeparturePlatformName() == null &&
                            getMappedStopId(stopPointRef).equals(stopPointRef.getValue())) {
                        stopPointRef.setValue(stopPointRef.getValue() + SEPARATOR + stopTimes.get(i).getStopId());
                    }
                }
            }


            List<ServiceDate> serviceDates = getServiceDates(serviceJourneyId);
            if (serviceDates.contains(serviceDate)) {
                boolean matchStopIdOnly = false;
                Set<EstimatedCall> visitedCalls = new HashSet<>();
                for (StopTime stopTime : stopTimes) {
                    for (EstimatedCall estimatedCall : estimatedCalls) {
                        if (visitedCalls.contains(estimatedCall)) {
                            continue;
                        }
                        String stopId = getMappedStopId(estimatedCall.getStopPointRef());

                        boolean isExtraCall = (estimatedCall.isExtraCall() != null && estimatedCall.isExtraCall());

                        if (isExtraCall || isMatch(matchStopIdOnly, stopTime, stopId, estimatedCall.getAimedArrivalTime(), estimatedCall.getAimedDepartureTime())) {
                            if (!matchStopIdOnly & !isExtraCall) {
                                //No longer check arrival-/departuretimes as they may deviate
                                matchStopIdOnly = true;
                            }
                            List<EstimatedCall> calls = matches.getOrDefault(serviceJourneyId, new ArrayList<>());
                            calls.add(estimatedCall);

                            visitedCalls.add(estimatedCall);

                            matches.put(serviceJourneyId, calls);
                            break;
                        }
                    }
                }
            }
        }

        return matches;
    }

    private Map<String, List<RecordedCall>> remapRecordedCalls(ServiceDate serviceDate, String etTrainNumber, EstimatedVehicleJourney.RecordedCalls recordedCallsWrapper) {

        Map<String, List<RecordedCall>> matches = new HashMap<>();

        if (recordedCallsWrapper == null) {
            return matches;
        }
        List<RecordedCall> recordedCalls = recordedCallsWrapper.getRecordedCalls();

        Set<String> serviceJourneyIds = getServiceJourney(etTrainNumber);

        for (String serviceJourneyId : serviceJourneyIds) {
            List<StopTime> stopTimes = getStopTimes(serviceJourneyId);

            // Override known unmappable stops
            if (recordedCalls.size() == stopTimes.size()) {
                for (int i = 0; i < recordedCalls.size(); i++) {
                    RecordedCall call = recordedCalls.get(i);
                    StopPointRef stopPointRef = call.getStopPointRef();

                    if (call.getArrivalPlatformName() == null &&
                            call.getDeparturePlatformName() == null &&
                            getMappedStopId(stopPointRef).equals(stopPointRef.getValue())) {
                        stopPointRef.setValue(stopPointRef.getValue() + SEPARATOR + stopTimes.get(i).getStopId());
                    }
                }
            }


            List<ServiceDate> serviceDates = getServiceDates(serviceJourneyId);
            if (serviceDates.contains(serviceDate)) {
                boolean matchStopIdOnly = false;
                Set<RecordedCall> visitedCall = new HashSet<>();
                for (StopTime stopTime : stopTimes) {
                    for (RecordedCall recordedCall : recordedCalls) {
                        if (visitedCall.contains(recordedCall)) {
                            continue;
                        }
                        String stopId = getMappedStopId(recordedCall.getStopPointRef());

                        boolean isExtraCall = (recordedCall.isExtraCall() != null && recordedCall.isExtraCall());

                        if (isExtraCall || isMatch(matchStopIdOnly, stopTime, stopId, recordedCall.getAimedArrivalTime(), recordedCall.getAimedDepartureTime())) {

                            if (!matchStopIdOnly & !isExtraCall) {
                                //No longer check arrival-/departuretimes as they may deviate
                                matchStopIdOnly = true;
                            }
                            List<RecordedCall> calls = matches.getOrDefault(serviceJourneyId, new ArrayList<>());
                            calls.add(recordedCall);

                            visitedCall.add(recordedCall);

                            matches.put(serviceJourneyId, calls);
                            break;
                        }
                    }
                }
            }
        }
        return matches;
    }

    protected boolean isMatch(boolean matchStopIdOnly, StopTime stopTime, String stopId, ZonedDateTime arrival, ZonedDateTime departure) {

        if (isStopIdOrParentMatch(stopId, stopTime.getStopId())) {

            if (matchStopIdOnly) {
                return true;

            } else {
                int arrivalSecondsSinceMidnight = getSecondsSinceMidnight(arrival);
                int departureSecondsSinceMidnight = getSecondsSinceMidnight(departure);

                int stopTimeArrivalTime = stopTime.getArrivalTime();
                int stopTimeDepartureTime = stopTime.getDepartureTime();

                // Trip may have started "yesterday" - subtract 24h to match SIRI timestamp
                if (stopTimeArrivalTime > 86400) {
                    stopTimeArrivalTime -= 86400;
                }
                if (stopTimeDepartureTime > 86400) {
                    stopTimeDepartureTime -= 86400;
                }

                //allows for 60 seconds difference in each direction as they sometimes differs a little bit...
                boolean arrivalMatch = arrivalSecondsSinceMidnight < 0 | Math.abs(arrivalSecondsSinceMidnight - stopTimeArrivalTime) < 60;
                boolean departureMatch = departureSecondsSinceMidnight < 0 | Math.abs(departureSecondsSinceMidnight - stopTimeDepartureTime) < 60;

                return (arrivalMatch || departureMatch);
            }
        }

        return false;
    }

    private String getMappedStopId(StopPointRef stopPointRef) {
        String stopIdRef = stopPointRef.getValue();
        if (stopIdRef.contains(SEPARATOR)) {
            return stopIdRef.substring(stopIdRef.lastIndexOf(SEPARATOR)+1);
        }
        return stopIdRef;
    }


    private static int getSecondsSinceMidnight(ZonedDateTime time) {
        if (time != null) {
            return time.getHour() * 3600 + time.getMinute() * 60 + time.getSecond();
        }
        return -1;
    }


}
