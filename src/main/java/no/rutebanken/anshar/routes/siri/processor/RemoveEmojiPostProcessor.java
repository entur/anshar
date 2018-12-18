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

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.DefaultedTextStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;

import java.util.List;

public class RemoveEmojiPostProcessor extends ValueAdapter implements PostProcessor {
    private Logger logger = LoggerFactory.getLogger(RemoveEmojiPostProcessor.class);

    private OutboundIdMappingPolicy outboundIdMappingPolicy;

    public RemoveEmojiPostProcessor(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (outboundIdMappingPolicy == OutboundIdMappingPolicy.DEFAULT) {
            //Only remove emojis when requested
            if (siri != null && siri.getServiceDelivery() != null) {

                List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
                if (situationExchangeDeliveries != null) {
                    for (SituationExchangeDeliveryStructure situationExchangeDelivery : situationExchangeDeliveries) {
                        SituationExchangeDeliveryStructure.Situations situations = situationExchangeDelivery.getSituations();
                        if (situations != null && situations.getPtSituationElements() != null) {
                            for (PtSituationElement ptSituationElement : situations.getPtSituationElements()) {
                                removeEmojisFromTexts(ptSituationElement.getSummaries());
                                removeEmojisFromTexts(ptSituationElement.getDescriptions());
                                removeEmojisFromTexts(ptSituationElement.getDetails());
                            }
                        }
                    }
                }
            }
        }
    }

    private void removeEmojisFromTexts(List<DefaultedTextStructure> textStructures) {
        if (textStructures != null) {
            for (DefaultedTextStructure text : textStructures) {
                String value = text.getValue();

                int[] ints = value.chars().map(i -> i > 500 ? ' ':i).toArray();

                String cleanedValue = new String();
                for (int i : ints) {
                    cleanedValue += (char) i;
                }
                if (!cleanedValue.equals(value)) {
                    logger.info("Removed emoji-like character from text [{}].", value);
                }
                text.setValue(cleanedValue);

            }
        }
    }
}
