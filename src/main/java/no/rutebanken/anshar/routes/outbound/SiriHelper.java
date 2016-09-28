package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.util.*;

public class SiriHelper {

    private static Logger logger = LoggerFactory.getLogger(SiriHelper.class);


    static Map<Class, Set<String>> getFilter(SubscriptionRequest subscriptionRequest) {
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0));
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
            if (containsValues(siri.getServiceDelivery().getVehicleMonitoringDeliveries())) {
                return applyVmFilter(siri, filter);
            } else if (containsValues(siri.getServiceDelivery().getSituationExchangeDeliveries())) {
                return applySxFilter(siri, filter);
            }
        }



        return null;
    }

    private static Siri applyVmFilter(Siri siri, Map<Class, Set<String>> filter) {

        filterLineRef(siri, filter.get(LineRef.class));
        filterVehicleRef(siri, filter.get(VehicleRef.class));

        return siri;
    }

    private static void filterLineRef(Siri siri, Set<String> linerefValues) {
        if (linerefValues == null || linerefValues.isEmpty()) {
            return;
        }
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
    }

    private static Siri applySxFilter(Siri siri, Map<Class, Set<String>> filter) {

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
