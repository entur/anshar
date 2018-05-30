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
    public byte[] createGtfsRt(Siri siri) {

        GtfsRealtime.FeedMessage.Builder builder = GtfsRealtime.FeedMessage.newBuilder();

        GtfsRealtime.FeedHeader.Builder header = GtfsRealtime.FeedHeader.newBuilder();
        header.setIncrementality(GtfsRealtime.FeedHeader.Incrementality.FULL_DATASET);
        header.setGtfsRealtimeVersion("2.0");
        builder.setHeader(header);

        ServiceDelivery serviceDelivery = siri.getServiceDelivery();
        //SIRI ET
        if (isNonNullNonEmptyList(serviceDelivery.getEstimatedTimetableDeliveries())) {
            for (EstimatedTimetableDeliveryStructure deliveryStructure : serviceDelivery.getEstimatedTimetableDeliveries()) {
                if (isNonNullNonEmptyList(deliveryStructure.getEstimatedJourneyVersionFrames())) {
                    for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : deliveryStructure.getEstimatedJourneyVersionFrames()) {
                        if (isNonNullNonEmptyList(estimatedVersionFrameStructure.getEstimatedVehicleJourneies())) {
                            for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVersionFrameStructure.getEstimatedVehicleJourneies()) {
                                GtfsRealtime.FeedEntity.Builder entity = GtfsRealtime.FeedEntity.newBuilder();
                                entity.setTripUpdate(tripUpdateFactory.createTripUpdateFromEstimatedVehicleJourney(estimatedVehicleJourney));

                                if (estimatedVehicleJourney.getDatedVehicleJourneyRef() != null) {
                                    entity.setId(estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue());
                                } else {
                                    entity.setId(estimatedVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
                                }
                                builder.addEntity(entity);
                            }
                        }
                    }
                }
            }
        }
//        else if (isNonNullNonEmptyList(serviceDelivery.getSituationExchangeDeliveries())) {
//            List<GtfsRealtime.Alert> alerts = new ArrayList<>();
//
//            for (SituationExchangeDeliveryStructure sxDeliveryStructure : serviceDelivery.getSituationExchangeDeliveries()) {
//                if (sxDeliveryStructure.getSituations() != null && isNonNullNonEmptyList(sxDeliveryStructure.getSituations().getPtSituationElements())) {
//                    for (PtSituationElement sx : sxDeliveryStructure.getSituations().getPtSituationElements()) {
//                        alerts.add(alertFactory.createAlertFromSituation(sx));
//                    }
//                }
//            }
//            System.out.println("Created alerts: " + alerts);
//        }
        if (builder.getEntityList().size() > 0) {
            return builder.build().toByteArray();
        }
        return null;
    }

    private boolean isNonNullNonEmptyList(List deliveries) {
        return deliveries != null && !deliveries.isEmpty();
    }
}
