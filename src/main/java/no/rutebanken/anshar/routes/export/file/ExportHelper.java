package no.rutebanken.anshar.routes.export.file;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.routes.export.gtfsrt.AlertFactory;
import no.rutebanken.anshar.routes.export.gtfsrt.TripUpdateFactory;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExportHelper {

    @Autowired
    private SiriHelper siriHelper;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    @Autowired
    private AlertFactory alertFactory;

    @Autowired
    private TripUpdateFactory tripUpdateFactory;

    public Siri exportET() {
        return transform(siriHelper.getAllET());
    }
    public Siri exportSX() {
        return transform(siriHelper.getAllSX());
    }
    public Siri exportVM() {
        return transform(siriHelper.getAllVM());
    }
    public Siri exportPT() {
        return transform(siriHelper.getAllPT());
    }

    private Siri transform(Siri body) {
        return SiriValueTransformer.transform(body, mappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT));
    }
    public void createGtfsRt(Siri siri) {
        ServiceDelivery serviceDelivery = siri.getServiceDelivery();
        //SIRI ET
        if (isNonNullNonEmptyList(serviceDelivery.getEstimatedTimetableDeliveries())) {
            for (EstimatedTimetableDeliveryStructure deliveryStructure : serviceDelivery.getEstimatedTimetableDeliveries()) {
                if (isNonNullNonEmptyList(deliveryStructure.getEstimatedJourneyVersionFrames())) {
                    for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : deliveryStructure.getEstimatedJourneyVersionFrames()) {
                        if (isNonNullNonEmptyList(estimatedVersionFrameStructure.getEstimatedVehicleJourneies())) {
                            for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVersionFrameStructure.getEstimatedVehicleJourneies()) {
//                                tripUpdateFactory.createTripUpdateEstimatedVehicleJourney(estimatedVehicleJourney)
                            }
                        }
                    }
                }
            }
        } else if (isNonNullNonEmptyList(serviceDelivery.getSituationExchangeDeliveries())) {
            List<GtfsRealtime.Alert> alerts = new ArrayList<>();

            for (SituationExchangeDeliveryStructure sxDeliveryStructure : serviceDelivery.getSituationExchangeDeliveries()) {
                if (sxDeliveryStructure.getSituations() != null && isNonNullNonEmptyList(sxDeliveryStructure.getSituations().getPtSituationElements())) {
                    for (PtSituationElement sx : sxDeliveryStructure.getSituations().getPtSituationElements()) {
                        alerts.add(alertFactory.createAlertFromSituation(sx));
                    }
                }
            }
            System.out.println("Created alerts: " + alerts);
        }
    }

    private boolean isNonNullNonEmptyList(List deliveries) {
        return deliveries != null && !deliveries.isEmpty();
    }
}
