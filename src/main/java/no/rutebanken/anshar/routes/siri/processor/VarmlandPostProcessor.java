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
import uk.org.siri.siri20.DataFrameRefStructure;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**

    Intended for mapping Swedish SIRI-data (Värmland)
    Current mappings:
    ET and VM:
        - Replaces prefix "SE:017" (Värmland region?) with "SE:050" (National?) for all ids

    VM:
        - Adds required "ValidUntil"-timestamp
        - Adds today's date as "DataFrameRef" to FramedVehicleJourneyRef (defines operating day for SJ)

 */
public class VarmlandPostProcessor extends ValueAdapter implements PostProcessor {

    private final String prefixPattern;
    private final String replacement;

    public VarmlandPostProcessor(String prefixPattern, String replacement) {
        this.prefixPattern = prefixPattern;
        this.replacement = replacement;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri != null && siri.getServiceDelivery() != null) {

            List<VehicleMonitoringDeliveryStructure> vmDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
            if (vmDeliveries != null) {
                for (VehicleMonitoringDeliveryStructure vmDelivery : vmDeliveries) {
                    List<VehicleActivityStructure> vehicleActivities = vmDelivery.getVehicleActivities();
                    for (VehicleActivityStructure vehicleActivity : vehicleActivities) {

                        if (vehicleActivity.getValidUntilTime() == null && vehicleActivity.getRecordedAtTime() != null) {
                            // Setting 10 minute validity by default
                            vehicleActivity.setValidUntilTime(vehicleActivity.getRecordedAtTime().plusMinutes(10));
                        }

                        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                        FramedVehicleJourneyRefStructure journeyRef = monitoredVehicleJourney.getFramedVehicleJourneyRef();
                        FramedVehicleJourneyRefStructure framedVehicleJourneyRefStructure = replacePrefix(journeyRef);
                        if (framedVehicleJourneyRefStructure.getDataFrameRef() == null) {
                            DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
                            // Guessing that OperatingDay is "today" for now
                            dataFrameRef.setValue(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                            framedVehicleJourneyRefStructure.setDataFrameRef(dataFrameRef);
                        }
                        monitoredVehicleJourney.setFramedVehicleJourneyRef(framedVehicleJourneyRefStructure);
                    }
                }
            }
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {

                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();

                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            estimatedVehicleJourney.setLineRef(replacePrefix(estimatedVehicleJourney.getLineRef()));
                            estimatedVehicleJourney.setFramedVehicleJourneyRef((replacePrefix(estimatedVehicleJourney.getFramedVehicleJourneyRef())));
                            EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney.getRecordedCalls();
                            if (recordedCalls != null) {
                                for (RecordedCall call : recordedCalls.getRecordedCalls()) {
                                    call.setStopPointRef(replacePrefix(call.getStopPointRef()));
                                }
                            }

                            EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();
                            if (estimatedCalls != null) {
                                for (EstimatedCall call : estimatedCalls.getEstimatedCalls()) {
                                    call.setStopPointRef(replacePrefix(call.getStopPointRef()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private StopPointRef replacePrefix(StopPointRef stopPointRef) {

        if (stopPointRef != null) {
            stopPointRef.setValue(stopPointRef.getValue().replaceFirst(prefixPattern, replacement));
            return stopPointRef;
        }
        return null;
    }

    private FramedVehicleJourneyRefStructure replacePrefix(FramedVehicleJourneyRefStructure framedVehicleJourneyRef) {
        if (framedVehicleJourneyRef != null) {
            framedVehicleJourneyRef.setDatedVehicleJourneyRef(
                    framedVehicleJourneyRef.getDatedVehicleJourneyRef().replaceFirst(prefixPattern, replacement)
            );
            return framedVehicleJourneyRef;
        }
        return null;
    }

    private LineRef replacePrefix(LineRef lineRef) {
        if (lineRef != null) {
            lineRef.setValue(lineRef.getValue().replaceFirst(prefixPattern, replacement));
            return lineRef;
        }
        return null;
    }
}
