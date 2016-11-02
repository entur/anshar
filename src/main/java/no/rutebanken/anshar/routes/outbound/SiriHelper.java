package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.jetbrains.annotations.NotNull;
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

    static Siri findInitialDeliveryData(OutboundSubscriptionSetup subscriptionRequest) {
        Siri delivery = null;

        switch (subscriptionRequest.getSubscriptionType()) {
            case SITUATION_EXCHANGE:

                List<PtSituationElement> situationElementList = Situations.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial SX-delivery: {} elements", situationElementList.size());
                delivery = SiriObjectFactory.createSXServiceDelivery(situationElementList);
                break;
            case VEHICLE_MONITORING:

                List<VehicleActivityStructure> vehicleActivities = VehicleActivities.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial VM-delivery: {} elements", vehicleActivities.size());
                delivery = SiriObjectFactory.createVMServiceDelivery(vehicleActivities);
                break;
            case ESTIMATED_TIMETABLE:


                List<EstimatedVehicleJourney> timetables = EstimatedTimetables.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial ET-delivery: {} elements", timetables.size());
                delivery = SiriObjectFactory.createETServiceDelivery(timetables);
                break;
            case PRODUCTION_TIMETABLE:

                List<ProductionTimetableDeliveryStructure> ptTimetables = ProductionTimetables.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial EPT-delivery: {} elements", ptTimetables.size());
                delivery = SiriObjectFactory.createPTServiceDelivery(ptTimetables);
                break;
        }
        return delivery;
    }

    public static List<Siri> splitDeliveries(Siri payload, int maximumSizePerDelivery) {

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
                siriList.add(SiriObjectFactory.createSXServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getVehicleMonitoringDeliveries())) {

            List<VehicleActivityStructure> vehicleActivities = payload.getServiceDelivery()
                    .getVehicleMonitoringDeliveries().get(0)
                    .getVehicleActivities();

            List<List> vmList = splitList(vehicleActivities, maximumSizePerDelivery);

            for (List<VehicleActivityStructure> list : vmList) {
                siriList.add(SiriObjectFactory.createVMServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getEstimatedTimetableDeliveries())) {

            List<EstimatedVehicleJourney> timetables = payload.getServiceDelivery()
                    .getEstimatedTimetableDeliveries().get(0)
                    .getEstimatedJourneyVersionFrames().get(0)
                    .getEstimatedVehicleJourneies();

            List<List> etList = splitList(timetables, maximumSizePerDelivery);

            for (List<EstimatedVehicleJourney> list : etList) {
                siriList.add(SiriObjectFactory.createETServiceDelivery(list));
            }
        }

        return siriList;
    }

    @NotNull
    private static List<List> splitList(List fullList, int maximumSizePerDelivery) {
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
            logger.info("No filter to apply");
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
