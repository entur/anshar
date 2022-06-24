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
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import java.util.List;

public class StringPrefixReplacerPostProcessor extends ValueAdapter implements PostProcessor {

    private final String pattern;
    private final String replacement;

    public StringPrefixReplacerPostProcessor(String pattern, String replacement) {
        this.pattern = pattern;
        this.replacement = replacement;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {

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
            stopPointRef.setValue(stopPointRef.getValue().replaceFirst(pattern, replacement));
            return stopPointRef;
        }
        return null;
    }

    private FramedVehicleJourneyRefStructure replacePrefix(FramedVehicleJourneyRefStructure framedVehicleJourneyRef) {
        if (framedVehicleJourneyRef != null) {
            framedVehicleJourneyRef.setDatedVehicleJourneyRef(
                    framedVehicleJourneyRef.getDatedVehicleJourneyRef().replaceFirst(pattern, replacement)
            );
            return framedVehicleJourneyRef;
        }
        return null;
    }

    private LineRef replacePrefix(LineRef lineRef) {
        if (lineRef != null) {
            lineRef.setValue(lineRef.getValue().replaceFirst(pattern, replacement));
            return lineRef;
        }
        return null;
    }
}
