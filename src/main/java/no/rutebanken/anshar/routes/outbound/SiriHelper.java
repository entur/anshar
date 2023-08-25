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

package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.AffectedLineStructure;
import uk.org.siri.siri21.AffectsScopeStructure;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedTimetableRequestStructure;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.LineDirectionStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SituationExchangeRequestStructure;
import uk.org.siri.siri21.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri21.SubscriptionRequest;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleMonitoringRequestStructure;
import uk.org.siri.siri21.VehicleMonitoringSubscriptionStructure;
import uk.org.siri.siri21.VehicleRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unchecked")
@Component
public class SiriHelper {

    public static final String FALLBACK_SIRI_VERSION = "2.1";
    private static final Logger logger = LoggerFactory.getLogger(SiriHelper.class);


    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    private final SiriObjectFactory siriObjectFactory;

    public SiriHelper(@Autowired SiriObjectFactory siriObjectFactory) {
        this.siriObjectFactory = siriObjectFactory;
    }


    Map<Class, Set<String>> getFilter(SubscriptionRequest subscriptionRequest) {
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0));
        }

        return new HashMap<>();
    }

    private Map<Class, Set<String>> getFilter(SituationExchangeSubscriptionStructure subscriptionStructure) {
        SituationExchangeRequestStructure situationExchangeRequest = subscriptionStructure.getSituationExchangeRequest();

        Map<Class, Set<String>> filterMap = new HashMap<>();
        List<LineRef> lineReves = situationExchangeRequest.getLineReves();
        if (lineReves != null) {
            Set<String> linerefValues = new HashSet<>();

            lineReves.forEach(ref ->
                            linerefValues.add(ref.getValue())
            );

            filterMap.put(LineRef.class, linerefValues);
        }
        return filterMap;
    }


    private Map<Class, Set<String>> getFilter(VehicleMonitoringSubscriptionStructure subscriptionStructure) {
        VehicleMonitoringRequestStructure vehicleMonitoringRequest = subscriptionStructure.getVehicleMonitoringRequest();

        Map<Class, Set<String>> filterMap = new HashMap<>();

        LineRef lineRef = vehicleMonitoringRequest.getLineRef();
        if (lineRef != null) {

            Set<String> linerefValues = new HashSet<>();
            linerefValues.add(lineRef.getValue());

            filterMap.put(LineRef.class, linerefValues);
        }
        return filterMap;
    }

    private Map<Class, Set<String>> getFilter(EstimatedTimetableSubscriptionStructure subscriptionStructure) {
        EstimatedTimetableRequestStructure request = subscriptionStructure.getEstimatedTimetableRequest();

        Map<Class, Set<String>> filterMap = new HashMap<>();
        Set<String> linerefValues = new HashSet<>();

        EstimatedTimetableRequestStructure.Lines lines = request.getLines();
        if (lines != null) {
            List<LineDirectionStructure> lineDirections = lines.getLineDirections();
            if (lineDirections != null) {
                for (LineDirectionStructure lineDirection : lineDirections) {
                    if (lineDirection.getLineRef() != null) {
                        linerefValues.add(lineDirection.getLineRef().getValue());
                    }
                }
            }

        }

        if (!linerefValues.isEmpty()) {
            filterMap.put(LineRef.class, linerefValues);
        }
        return filterMap;
    }

    Siri findInitialDeliveryData(OutboundSubscriptionSetup subscriptionRequest) {
        Siri delivery = null;

        switch (subscriptionRequest.getSubscriptionType()) {
            case SITUATION_EXCHANGE:

                Collection<PtSituationElement> situationElementList = situations.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial SX-delivery: {} elements", situationElementList.size());
                delivery = siriObjectFactory.createSXServiceDelivery(situationElementList);
                break;
            case VEHICLE_MONITORING:

                Collection<VehicleActivityStructure> vehicleActivityList = vehicleActivities.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial VM-delivery: {} elements", vehicleActivityList.size());
                delivery = siriObjectFactory.createVMServiceDelivery(vehicleActivityList);
                break;
            case ESTIMATED_TIMETABLE:

                Collection<EstimatedVehicleJourney> timetables = estimatedTimetables.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial ET-delivery: {} elements", timetables.size());
                delivery = siriObjectFactory.createETServiceDelivery(timetables);
                break;
        }
        return delivery;
    }

    public List<Siri> splitDeliveries(Siri payload, int maximumSizePerDelivery) {

        List<Siri> siriList = new ArrayList<>();

        if (payload.getServiceDelivery() == null) {
            siriList.add(payload);
            return siriList;
        }

        if (containsValues(payload.getServiceDelivery().getSituationExchangeDeliveries())) {

            List<PtSituationElement> situationElementList = payload.getServiceDelivery()
                    .getSituationExchangeDeliveries().get(0)
                    .getSituations()
                    .getPtSituationElements();

            List<List> sxList = splitList(situationElementList, maximumSizePerDelivery);

            for (List<PtSituationElement> list : sxList) {
                siriList.add(siriObjectFactory.createSXServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getVehicleMonitoringDeliveries())) {

            List<VehicleActivityStructure> vehicleActivities = payload.getServiceDelivery()
                    .getVehicleMonitoringDeliveries().get(0)
                    .getVehicleActivities();

            List<List> vmList = splitList(vehicleActivities, maximumSizePerDelivery);

            for (List<VehicleActivityStructure> list : vmList) {
                siriList.add(siriObjectFactory.createVMServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getEstimatedTimetableDeliveries())) {

            List<EstimatedVehicleJourney> timetables = payload.getServiceDelivery()
                    .getEstimatedTimetableDeliveries().get(0)
                    .getEstimatedJourneyVersionFrames().get(0)
                    .getEstimatedVehicleJourneies();

            List<List> etList = splitList(timetables, maximumSizePerDelivery);

            for (List<EstimatedVehicleJourney> list : etList) {
                siriList.add(siriObjectFactory.createETServiceDelivery(list));
            }
        }

        return siriList;
    }

    private List<List> splitList(List fullList, int maximumSizePerDelivery) {
        int startIndex = 0;
        int endIndex = Math.min(startIndex + maximumSizePerDelivery, fullList.size());

        List<List> list = new ArrayList<>();
        boolean hasMoreElements = true;
        while(hasMoreElements) {

            list.add(fullList.subList(startIndex, endIndex));
            if (endIndex >= fullList.size()) {
                hasMoreElements = false;
            }
            startIndex += maximumSizePerDelivery;
            endIndex = Math.min(startIndex + maximumSizePerDelivery, fullList.size());
        }
        return list;
    }

    static boolean containsValues(List list) {
        return (list != null && !list.isEmpty());
    }

    public static Siri filterSiriPayload(Siri siri, Map<Class, Set<String>> filter) {
        if (filter == null || filter.isEmpty()) {
            logger.debug("No filter to apply");
            return siri;
        }

        if (siri.getServiceDelivery() != null) {

            Siri filtered;
            try {
                filtered = SiriObjectFactory.deepCopy(siri);
            } catch (Exception e) {
                return siri;
            }

            if (containsValues(filtered.getServiceDelivery().getVehicleMonitoringDeliveries()) |
                    containsValues(filtered.getServiceDelivery().getEstimatedTimetableDeliveries())) {
                return applySingleMatchFilter(filtered, filter);
            } else if (containsValues(filtered.getServiceDelivery().getSituationExchangeDeliveries())) {
                return applyMultipleMatchFilter(filtered, filter);
            }
        }

        return siri;
    }

    /*
     * Filters elements with 1 - one - possible match per element
     */
    private static Siri applySingleMatchFilter(Siri siri, Map<Class, Set<String>> filter) {


        filterLineRef(siri, filter.get(LineRef.class));
        filterVehicleRef(siri, filter.get(VehicleRef.class));

        return siri;
    }

    private static void filterLineRef(Siri siri, Set<String> linerefValues) {
        if (linerefValues == null || linerefValues.isEmpty()) {
            return;
        }

        //VM-deliveries
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
        for (VehicleMonitoringDeliveryStructure delivery : vehicleMonitoringDeliveries) {
            List<VehicleActivityStructure> vehicleActivities = delivery.getVehicleActivities();
            List<VehicleActivityStructure> filteredActivities = new ArrayList<>();

            for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                if (monitoredVehicleJourney != null) {
                    if (monitoredVehicleJourney.getLineRef() != null) {
                        if (isLineRefMatch(linerefValues, monitoredVehicleJourney.getLineRef().getValue())) {
                            filteredActivities.add(vehicleActivity);
                        }
                    }
                }
            }
            delivery.getVehicleActivities().clear();
            delivery.getVehicleActivities().addAll(filteredActivities);
        }

        //ET-deliveries
        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        for (EstimatedTimetableDeliveryStructure delivery : etDeliveries) {
            List<EstimatedVersionFrameStructure> etVersionFrames = delivery.getEstimatedJourneyVersionFrames();

            for (EstimatedVersionFrameStructure version : etVersionFrames) {
                List<EstimatedVehicleJourney> matches = new ArrayList<>();
                List<EstimatedVehicleJourney> estimatedVehicleJourneies = version.getEstimatedVehicleJourneies();
                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                    if (estimatedVehicleJourney.getLineRef() != null) {
                        if (isLineRefMatch(linerefValues, estimatedVehicleJourney.getLineRef().getValue())) {
                            matches.add(estimatedVehicleJourney);
                        }
                    }
                }
                version.getEstimatedVehicleJourneies().clear();
                version.getEstimatedVehicleJourneies().addAll(matches);
            }
        }
    }

    private static boolean isLineRefMatch(Set<String> linerefValues, String completeValue) {
        return linerefValues.contains(completeValue);
    }

    private static void filterVehicleRef(Siri siri, Set<String> vehiclerefValues) {
        if (vehiclerefValues == null || vehiclerefValues.isEmpty()) {
            return;
        }
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
        for (VehicleMonitoringDeliveryStructure delivery : vehicleMonitoringDeliveries) {
            List<VehicleActivityStructure> vehicleActivities = delivery.getVehicleActivities();
            List<VehicleActivityStructure> filteredActivities = new ArrayList<>();

            for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                if (monitoredVehicleJourney != null) {
                    if (monitoredVehicleJourney.getVehicleRef() != null) {
                        if (vehiclerefValues.contains(monitoredVehicleJourney.getVehicleRef().getValue())) {
                            filteredActivities.add(vehicleActivity);
                        }
                    }
                }
            }
            delivery.getVehicleActivities().clear();
            delivery.getVehicleActivities().addAll(filteredActivities);
        }

        //ET-deliveries
        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        for (EstimatedTimetableDeliveryStructure delivery : etDeliveries) {
            List<EstimatedVersionFrameStructure> etVersionFrames = delivery.getEstimatedJourneyVersionFrames();

            for (EstimatedVersionFrameStructure version : etVersionFrames) {
                List<EstimatedVehicleJourney> matches = new ArrayList<>();
                List<EstimatedVehicleJourney> estimatedVehicleJourneies = version.getEstimatedVehicleJourneies();
                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                    if (estimatedVehicleJourney.getVehicleRef() != null) {
                        if (vehiclerefValues.contains(estimatedVehicleJourney.getVehicleRef().getValue())) {
                            matches.add(estimatedVehicleJourney);
                        }
                    }
                }
                version.getEstimatedVehicleJourneies().clear();
                version.getEstimatedVehicleJourneies().addAll(matches);
            }
        }
    }


    /*
     * Filters elements with multiple possible matches per element
     */
    private static Siri applyMultipleMatchFilter(Siri siri, Map<Class, Set<String>> filter) {

        Set<String> linerefValues = filter.get(LineRef.class);
        if (linerefValues == null || linerefValues.isEmpty()) {
            return siri;
        }
        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
        for (SituationExchangeDeliveryStructure delivery : situationExchangeDeliveries) {
            SituationExchangeDeliveryStructure.Situations situations = delivery.getSituations();


            List<PtSituationElement> ptSituationElements = situations.getPtSituationElements();
            List<PtSituationElement> filteredSituationElements = new ArrayList<>();
            for (PtSituationElement s : ptSituationElements) {
                if (s.getAffects() != null &&
                        s.getAffects().getNetworks() != null &&
                        s.getAffects().getNetworks().getAffectedNetworks() != null) {

                    List<AffectsScopeStructure.Networks.AffectedNetwork> affectedNetworks = s.getAffects().getNetworks().getAffectedNetworks();
                    for (AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork : affectedNetworks) {
                        List<AffectedLineStructure> affectedLines = affectedNetwork.getAffectedLines();
                        if (affectedLines != null) {
                            for (AffectedLineStructure affectedLine : affectedLines) {
                                LineRef lineRef = affectedLine.getLineRef();
                                if (!filteredSituationElements.contains(s) && lineRef != null) {
                                    if (isLineRefMatch(linerefValues, lineRef.getValue())) {
                                        filteredSituationElements.add(s);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            situations.getPtSituationElements().clear();
            situations.getPtSituationElements().addAll(filteredSituationElements);
        }

        return siri;
    }

    public static String resolveSiriVersionStr(SiriValidator.Version version) {
        switch (version) {
            case VERSION_1_0:
                return "1.0";
            case VERSION_1_3:
                return "1.3";
            case VERSION_1_4:
                return "1.4";
            case VERSION_2_0:
                return "2.0";
            case VERSION_2_1:
                return "2.1";
            default:
                return FALLBACK_SIRI_VERSION;
        }
    }

    public Siri getAllVM() {
        return siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAll());
    }
    public Siri getAllSX() {
        return siriObjectFactory.createSXServiceDelivery(situations.getAll());
    }
    public Siri getAllET() {
        return siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAll());
    }
}
