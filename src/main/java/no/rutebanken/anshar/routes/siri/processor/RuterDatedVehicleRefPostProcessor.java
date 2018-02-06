package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.*;

import java.util.List;
import java.util.StringTokenizer;

public class RuterDatedVehicleRefPostProcessor extends ValueAdapter implements PostProcessor {

    private static final String DELIMITER = ":";
    private static final String SERVICE_JOURNEY_PREFIX = "RUT:ServiceJourney:";

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (estimatedTimetableDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                    if (estimatedJourneyVersionFrames != null) {
                        for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : estimatedJourneyVersionFrames) {
                            if (estimatedVersionFrameStructure != null) {
                                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVersionFrameStructure.getEstimatedVehicleJourneies()) {
                                    if (estimatedVehicleJourney.getFramedVehicleJourneyRef() != null) {
                                        String updatedVehicleJourneyRef = createNewVehicleJourneyRef(estimatedVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
                                        if (updatedVehicleJourneyRef != null) {
                                            estimatedVehicleJourney.getFramedVehicleJourneyRef().setDatedVehicleJourneyRef(updatedVehicleJourneyRef);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
            if (vehicleMonitoringDeliveries != null) {
                for (VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery : vehicleMonitoringDeliveries) {
                    List<VehicleActivityStructure> vehicleActivities = vehicleMonitoringDelivery.getVehicleActivities();
                    if (vehicleActivities != null) {
                        for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                            VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                            if (monitoredVehicleJourney != null) {
                                if (monitoredVehicleJourney.getFramedVehicleJourneyRef() != null) {
                                    String updatedVehicleJourneyRef = createNewVehicleJourneyRef(monitoredVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
                                    if (updatedVehicleJourneyRef != null) {
                                        monitoredVehicleJourney.getFramedVehicleJourneyRef().setDatedVehicleJourneyRef(updatedVehicleJourneyRef);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private String createNewVehicleJourneyRef(String datedVehicleJourneyRef) {
        String result = null;
        if (datedVehicleJourneyRef != null && !datedVehicleJourneyRef.startsWith(SERVICE_JOURNEY_PREFIX)) {
            StringTokenizer tokenizer = new StringTokenizer(datedVehicleJourneyRef, DELIMITER);
            if (tokenizer.countTokens() >= 2) {
                String newValue = SERVICE_JOURNEY_PREFIX + tokenizer.nextToken() + "-" + tokenizer.nextToken();
                result = datedVehicleJourneyRef + SiriValueTransformer.SEPARATOR + newValue;
            }
        }
        return result;
    }
    @Override
    protected String apply(String value) {
        return null;
    }
}
