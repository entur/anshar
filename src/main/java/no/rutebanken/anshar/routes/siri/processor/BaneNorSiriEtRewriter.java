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

import no.rutebanken.anshar.data.collections.KryoSerializer;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.routedata.ServiceDate;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopTime;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.DatedVehicleJourneyRef;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getPublicCode;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getServiceDates;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getServiceJourney;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getStopTimes;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.isDsjCancelled;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.isKnownTrainNr;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.isStopIdOrParentMatch;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.REMOVE_UNKNOWN_DEPARTURE;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.RESTRUCTURE_DEPARTURE;
import static no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer.SEPARATOR;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.createCombinedId;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

/**
 * Rewrites the SIRI ET-feed from BaneNOR to match the planned routes received from NSB
 *
 */
public class BaneNorSiriEtRewriter extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BaneNorSiriEtRewriter.class);

    /*
     * List of provided stops without actual realtime-data.
     * Reported platform for these stops will be overwritten by planned platform from NeTEx-data
     */
    private static final transient List<String> foreignStops = Arrays.asList(
                                                                        "GTB",  // Gøteborg,
                                                                        "TRL",  // Trollhættan
                                                                        "ED",   // Ed
                                                                        "CG",   // Charlottenberg
                                                                        "STR",  // Storlien
                                                                        "ØXN"); // Øxnered
    private String datasetId;

    private transient KryoSerializer kryoSerializer;

    public BaneNorSiriEtRewriter(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        long startTime = System.currentTimeMillis();
        int previousSize = 0;
        int newSize = 0;

        if (kryoSerializer == null) {
            kryoSerializer = new KryoSerializer();
        }

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
                                    ServiceDate serviceDate = getServiceDate(estimatedVehicleJourney);

                                    if (estimatedVehicleJourney.isExtraJourney() != null && estimatedVehicleJourney.isExtraJourney()) {
                                        //Extra journey - ignore comparison to planned data
                                        foundMatch = true;
                                    } else {
                                        Set<String> serviceJourneys = getServiceJourney(etTrainNumber);
                                        for (String serviceJourney : serviceJourneys) {
                                            List<ServiceDate> serviceDates = getServiceDates(serviceJourney);
                                            if (serviceDates.contains(serviceDate)) {
                                                if (!isDsjCancelled(serviceJourney, serviceDate)) {
                                                    foundMatch = true;
                                                    break;
                                                } else {
                                                    logger.info("Skipping departure cancelled in DSJ: {} - {} ", serviceJourney, serviceDate);
                                                }
                                            }
                                        }
                                    }


                                    if (foundMatch) {
                                        restructuredJourneyList.put(etTrainNumber, reStructureEstimatedJourney(estimatedVehicleJourney, etTrainNumber));
                                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, RESTRUCTURE_DEPARTURE, 1);
                                    } else {
                                        logger.warn("Ignoring realtime-data for departure not found in NeTEx for given day - train number {}, {}", etTrainNumber, serviceDate);
                                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, REMOVE_UNKNOWN_DEPARTURE, 1);
                                    }

                                }  else if (etTrainNumber.length() == 5 && (etTrainNumber.startsWith("905") || etTrainNumber.startsWith("908"))) {
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
        ServiceDate serviceDate = getServiceDate(estimatedVehicleJourney);

        Map<String, List<EstimatedCall>> remappedEstimatedCalls = new HashMap<>();
        Map<String, List<RecordedCall>> remappedRecordedCalls = new HashMap<>();
        if (serviceDate != null) {
            Set<String> serviceJourneyIds = getServiceJourney(etTrainNumber);
            serviceJourneyIds.removeIf(sjId -> isDsjCancelled(sjId, serviceDate));;

            remappedRecordedCalls.putAll(remapRecordedCalls(serviceDate, serviceJourneyIds, estimatedVehicleJourney.getRecordedCalls()));
            remappedEstimatedCalls.putAll(remapEstimatedCalls(serviceDate, serviceJourneyIds, estimatedVehicleJourney.getEstimatedCalls()));
        }
        if (remappedRecordedCalls.isEmpty() && remappedEstimatedCalls.isEmpty()) {
            // Found match with no RecordedCalls and no EstimatedCalls - keep data unchanged
            restructuredJourneyList.add(estimatedVehicleJourney);
        } else {
            if (!remappedEstimatedCalls.isEmpty()) {
                // EstimatedCalls exist - loop through all remapped journeys
                for (String id : remappedEstimatedCalls.keySet()) {
                    List<RecordedCall> recordedCalls = remappedRecordedCalls.get(id);
                    List<EstimatedCall> estimatedCalls = remappedEstimatedCalls.get(id);

                    int order = 1;
                    if (recordedCalls != null) {
                        for (RecordedCall recordedCall : recordedCalls) {
                            recordedCall.setOrder(BigInteger.valueOf(order++));
                        }
                    }
                    for (EstimatedCall estimatedCall : estimatedCalls) {
                        estimatedCall.setOrder(BigInteger.valueOf(order++));
                    }

                    EstimatedVehicleJourney journey = createCopyOfEstimatedVehicleJourney(estimatedVehicleJourney);

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
            } else {
                // Only RecordedCalls exist for journey -
                for (String id : remappedRecordedCalls.keySet()) {
                    List<RecordedCall> recordedCalls = remappedRecordedCalls.get(id);

                    int order = 1;
                    if (recordedCalls != null) {
                        for (RecordedCall recordedCall : recordedCalls) {
                            recordedCall.setOrder(BigInteger.valueOf(order++));
                        }
                    }

                    EstimatedVehicleJourney journey = createCopyOfEstimatedVehicleJourney(estimatedVehicleJourney);

                    if (recordedCalls != null) {
                        EstimatedVehicleJourney.RecordedCalls restructuredRecordedCalls = new EstimatedVehicleJourney.RecordedCalls();
                        restructuredRecordedCalls.getRecordedCalls().addAll(recordedCalls);
                        journey.setRecordedCalls(restructuredRecordedCalls);
                    }

                    restructuredJourneyList.add(journey);
                }
            }
        }
        return restructuredJourneyList;
    }

    private EstimatedVehicleJourney createCopyOfEstimatedVehicleJourney(EstimatedVehicleJourney estimatedVehicleJourney) {

        return SiriObjectFactory.deepCopy(estimatedVehicleJourney);
    }

    protected static ServiceDate getServiceDate(EstimatedVehicleJourney estimatedVehicleJourney) {
        DatedVehicleJourneyRef datedVehicleJourneyRef = estimatedVehicleJourney.getDatedVehicleJourneyRef();
        if (datedVehicleJourneyRef != null) {
            String value = datedVehicleJourneyRef.getValue();
            String serviceDate = value.substring(value.indexOf(":") + 1);
            if (serviceDate != null && serviceDate.indexOf("-") > 0) {
                String[] dateParts = serviceDate.split("-");
                if (dateParts.length == 3) {
                    try {
                        return new ServiceDate(
                                Integer.parseInt(dateParts[0]),
                                Integer.parseInt(dateParts[1]),
                                Integer.parseInt(dateParts[2])
                        );
                    } catch (NumberFormatException e) {
                        // Ignore - fallback to date-calculation
                    }
                }
            }
        }

        // ServiceDate not resolved by parsing DatedVehicleJourneyRef - calculating based on first stop instead

        ZonedDateTime departureTime = getFirstDepartureTime(estimatedVehicleJourney);
        return new ServiceDate(departureTime.getYear(), departureTime.getMonthValue(), departureTime.getDayOfMonth());
    }

    protected static ZonedDateTime getFirstDepartureTime(EstimatedVehicleJourney estimatedVehicleJourney) {
        ZonedDateTime departureTime;
        if (estimatedVehicleJourney.getRecordedCalls() != null && !estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
            departureTime = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
            if (departureTime == null) {
                departureTime = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().get(0).getAimedArrivalTime();
            }
        } else {
            departureTime = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls().get(0).getAimedDepartureTime();
            if (departureTime == null) {
                departureTime = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls().get(0).getAimedArrivalTime();
            }
        }
        return departureTime;
    }

    private Map<String, List<EstimatedCall>> remapEstimatedCalls(ServiceDate serviceDate, Set<String> serviceJourneyIds, EstimatedVehicleJourney.EstimatedCalls estimatedCallsWrapper) {

        Map<String, List<EstimatedCall>> matches = new HashMap<>();

        if (estimatedCallsWrapper == null) {
            return matches;
        }
        List<EstimatedCall> estimatedCalls = estimatedCallsWrapper.getEstimatedCalls();


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
                        stopPointRef.setValue(createCombinedId(stopPointRef.getValue(), stopTimes.get(i).getStopId()));
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
                            if (!matchStopIdOnly && !isExtraCall) {
                                //No longer check arrival-/departuretimes as they may deviate
                                matchStopIdOnly = true;
                            }

                            String originalId = getOriginalId(estimatedCall.getStopPointRef().getValue());
                            if (foreignStops.contains(originalId)) {
                                stopId = stopTime.getStopId();

                                String publicCode = getPublicCode(stopId);
                                if (publicCode != null && !publicCode.isEmpty()) {
                                    NaturalLanguageStringStructure platform = new NaturalLanguageStringStructure();
                                    platform.setValue(publicCode);
                                    if (estimatedCall.getDeparturePlatformName() != null && !"".equals(estimatedCall.getDeparturePlatformName().getValue())) {
                                        estimatedCall.setDeparturePlatformName(platform);
                                    }
                                    if (estimatedCall.getArrivalPlatformName() != null && !"".equals(estimatedCall.getArrivalPlatformName().getValue())) {
                                        estimatedCall.setArrivalPlatformName(platform);
                                    }
                                }
                                StopPointRef stopPointRef = new StopPointRef();
                                stopPointRef.setValue(createCombinedId(originalId, stopId));
                                estimatedCall.setStopPointRef(stopPointRef);
                            }

                            List<EstimatedCall> calls = matches.getOrDefault(serviceJourneyId, new ArrayList<>());
                            final byte[] bytes = kryoSerializer.write(estimatedCall);
                            calls.add((EstimatedCall) kryoSerializer.read(bytes));

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

    private Map<String, List<RecordedCall>> remapRecordedCalls(ServiceDate serviceDate, Set<String> serviceJourneyIds, EstimatedVehicleJourney.RecordedCalls recordedCallsWrapper) {

        Map<String, List<RecordedCall>> matches = new HashMap<>();

        if (recordedCallsWrapper == null) {
            return matches;
        }
        List<RecordedCall> recordedCalls = recordedCallsWrapper.getRecordedCalls();

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
                        stopPointRef.setValue(createCombinedId(stopPointRef.getValue(), stopTimes.get(i).getStopId()));
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

                            if (!matchStopIdOnly && !isExtraCall) {
                                //No longer check arrival-/departuretimes as they may deviate
                                matchStopIdOnly = true;
                            }

                            String originalId = getOriginalId(recordedCall.getStopPointRef().getValue());
                            if (foreignStops.contains(originalId)) {
                                stopId = stopTime.getStopId();

                                String publicCode = getPublicCode(stopId);
                                if (publicCode != null && !publicCode.isEmpty()) {
                                    NaturalLanguageStringStructure platform = new NaturalLanguageStringStructure();
                                    platform.setValue(publicCode);
                                    if (recordedCall.getDeparturePlatformName() != null && !"".equals(recordedCall.getDeparturePlatformName().getValue())) {
                                        recordedCall.setDeparturePlatformName(platform);
                                    }
                                    if (recordedCall.getArrivalPlatformName() != null && !"".equals(recordedCall.getArrivalPlatformName().getValue())) {
                                        recordedCall.setArrivalPlatformName(platform);
                                    }
                                }
                                StopPointRef stopPointRef = new StopPointRef();
                                stopPointRef.setValue(createCombinedId(originalId, stopId));
                                recordedCall.setStopPointRef(stopPointRef);
                            }

                            List<RecordedCall> calls = matches.getOrDefault(serviceJourneyId, new ArrayList<>());
                            final byte[] bytes = kryoSerializer.write(recordedCall);
                            calls.add((RecordedCall) kryoSerializer.read(bytes));


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
                boolean arrivalMatch = arrivalSecondsSinceMidnight < 0 || Math.abs(arrivalSecondsSinceMidnight - stopTimeArrivalTime) < 60;
                boolean departureMatch = departureSecondsSinceMidnight < 0 || Math.abs(departureSecondsSinceMidnight - stopTimeDepartureTime) < 60;

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
