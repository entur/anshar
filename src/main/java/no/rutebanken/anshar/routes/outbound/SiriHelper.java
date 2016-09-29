package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.util.*;

public class SiriHelper {

    private static Logger logger = LoggerFactory.getLogger(SiriHelper.class);


    static Map<Class, Set<String>> getFilter(SubscriptionRequest subscriptionRequest) {
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0));
        }

        return new HashMap<>();
    }

    private static Map<Class, Set<String>> getFilter(SituationExchangeSubscriptionStructure subscriptionStructure) {
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


    private static Map<Class, Set<String>> getFilter(VehicleMonitoringSubscriptionStructure subscriptionStructure) {
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

    private static Map<Class, Set<String>> getFilter(EstimatedTimetableSubscriptionStructure subscriptionStructure) {
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

    static Siri findInitialDeliveryData(SubscriptionRequest subscriptionRequest) {
        Siri delivery = null;
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            List<PtSituationElement> situationElementList = Situations.getAll();
            logger.info("Initial PT-delivery: {} elements", situationElementList.size());
            delivery = SiriObjectFactory.createSXServiceDelivery(situationElementList);
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            List<VehicleActivityStructure> vehicleActivities = VehicleActivities.getAll();
            logger.info("Initial VM-delivery: {} elements", vehicleActivities.size());
            delivery = SiriObjectFactory.createVMServiceDelivery(vehicleActivities);
        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            List<EstimatedVehicleJourney> timetables = EstimatedTimetables.getAll();
            logger.info("Initial ET-delivery: {} elements", timetables.size());
            delivery = SiriObjectFactory.createETServiceDelivery(timetables);
        }
        return delivery;
    }

    static boolean containsValues(List list) {
        return (list != null && !list.isEmpty());
    }

    public static Siri filterSiriPayload(Siri siri, Map<Class, Set<String>> filter) {
        if (filter == null || filter.isEmpty()) {
            return siri;
        }

        if (siri.getServiceDelivery() != null) {
            if (containsValues(siri.getServiceDelivery().getVehicleMonitoringDeliveries()) |
                    containsValues(siri.getServiceDelivery().getEstimatedTimetableDeliveries())) {
                return applySingleMatchFilter(siri, filter);
            } else if (containsValues(siri.getServiceDelivery().getSituationExchangeDeliveries())) {
                return applyMultipleMatchFilter(siri, filter);
            }
        }



        return siri;
    }

    /*
     * Filters elements with 1 - one - possible match per element
     */
    private static Siri applySingleMatchFilter(Siri siri, Map<Class, Set<String>> filter) {

        Siri filtered;
        try {
            filtered = SiriObjectFactory.deepCopy(siri);
        } catch (JAXBException e) {
            return siri;
        }

        filterLineRef(filtered, filter.get(LineRef.class));
        filterVehicleRef(filtered, filter.get(VehicleRef.class));

        return filtered;
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
                        if (linerefValues.contains(monitoredVehicleJourney.getLineRef().getValue())) {
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
                        if (linerefValues.contains(estimatedVehicleJourney.getLineRef().getValue())) {
                            matches.add(estimatedVehicleJourney);
                        }
                    }
                }
                version.getEstimatedVehicleJourneies().clear();
                version.getEstimatedVehicleJourneies().addAll(matches);
            }
        }
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
                                List<LineRef> lineReves = affectedLine.getLineReves();
                                if (lineReves != null) {
                                    for (LineRef lineRef : lineReves) {
                                        if (linerefValues.contains(lineRef.getValue())) {
                                            if (!filteredSituationElements.contains(s)) {
                                                filteredSituationElements.add(s);
                                            }
                                        }
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

}
