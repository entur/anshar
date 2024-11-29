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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleModesEnumeration;

import java.util.List;

public class ModeBlackListProcessor extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ModeBlackListProcessor.class);
    private final String codespace;
    private final List<String> blacklist;

    public ModeBlackListProcessor(String codespace, List<String> blacklist) {
        this.codespace = codespace;
        this.blacklist = blacklist;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (this.blacklist == null || this.blacklist.isEmpty()) {
            //Nothing to do - return immediately
            return;
        }
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                        int size = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();

                        estimatedJourneyVersionFrame
                                .getEstimatedVehicleJourneies()
                                .removeIf(et -> isInvalidMode(et.getVehicleModes()));

                        if (estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size() != size) {
                            final int removedDataCount = size - estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().size();
                            logger.info("Removed {} ET-messages on blacklisted modes from {}.", removedDataCount, codespace);
                        }
                    }
                }
            }
        }

    }

    private boolean isInvalidMode(List<VehicleModesEnumeration> modes) {
        if (modes != null || modes.size() > 1) {
            if (toBeRemoved(modes.get(0).value())) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean toBeRemoved(String mode) {
        if (mode != null) {
            return blacklist.contains(mode);
        }
        return false;
    }

}
