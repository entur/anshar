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
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;
import java.util.List;

public class RuterMetroRoundSecondsPostProcessor extends ValueAdapter implements PostProcessor{


    private static final int SECONDS_ROUND_LIMIT = 45;

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

                                    EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney.getRecordedCalls();
                                    if (recordedCalls != null && recordedCalls.getRecordedCalls() != null) {
                                        for (RecordedCall call : recordedCalls.getRecordedCalls()) {
                                            call.setAimedArrivalTime(roundSeconds(call.getAimedArrivalTime()));
                                            call.setAimedDepartureTime(roundSeconds(call.getAimedDepartureTime()));
                                        }
                                    }

                                    EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();
                                    if (estimatedCalls != null && estimatedCalls.getEstimatedCalls() != null) {
                                        for (EstimatedCall call : estimatedCalls.getEstimatedCalls()) {
                                            call.setAimedArrivalTime(roundSeconds(call.getAimedArrivalTime()));
                                            call.setAimedDepartureTime(roundSeconds(call.getAimedDepartureTime()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static ZonedDateTime roundSeconds(ZonedDateTime time) {

        if (time == null) {
            return null;
        }

        ZonedDateTime timeWithoutSeconds;

        if (time.getSecond() >= SECONDS_ROUND_LIMIT) {
            timeWithoutSeconds = time.plusSeconds(60-time.getSecond());
        } else {
            timeWithoutSeconds = time.minusSeconds(time.getSecond());
        }

        return timeWithoutSeconds.minusNanos(time.getNano());
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
