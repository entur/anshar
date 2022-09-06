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

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import java.time.ZonedDateTime;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.CREATE_RECORDED_AT_TIME;

public class OstfoldVmPostProcessor extends ValueAdapter implements PostProcessor {

    private final String datasetId;

    public OstfoldVmPostProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {

            List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
            if (vehicleMonitoringDeliveries != null) {
                for (VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery : vehicleMonitoringDeliveries) {
                    List<VehicleActivityStructure> vehicleActivities = vehicleMonitoringDelivery.getVehicleActivities();
                    if (vehicleActivities != null) {
                        for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                            if (vehicleActivity.getRecordedAtTime().getYear() == 1) {
                                vehicleActivity.setRecordedAtTime(ZonedDateTime.now());
                                getMetricsService().registerDataMapping(SiriDataType.VEHICLE_MONITORING, datasetId, CREATE_RECORDED_AT_TIME, 1);
                            }
                        }
                    }
                }
            }
        }
    }
}
