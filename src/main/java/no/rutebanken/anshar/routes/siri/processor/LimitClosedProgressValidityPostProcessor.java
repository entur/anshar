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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.time.ZonedDateTime;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.LIMIT_CLOSED_SX_MESSAGE_END_TIME;

public class LimitClosedProgressValidityPostProcessor extends ValueAdapter implements PostProcessor {
    private static final long TIME_LIMIT_HOURS = 5;
    private final String datasetId;
    private Logger logger = LoggerFactory.getLogger(LimitClosedProgressValidityPostProcessor.class);

    public LimitClosedProgressValidityPostProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri != null && siri.getServiceDelivery() != null) {

            List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
            if (situationExchangeDeliveries != null) {
                for (SituationExchangeDeliveryStructure situationExchangeDelivery : situationExchangeDeliveries) {
                    SituationExchangeDeliveryStructure.Situations situations = situationExchangeDelivery.getSituations();
                    if (situations != null && situations.getPtSituationElements() != null) {
                        for (PtSituationElement ptSituationElement : situations.getPtSituationElements()) {
                            if (ptSituationElement.getProgress() == WorkflowStatusEnumeration.CLOSED) {
                                ZonedDateTime expiration = getLastEndTime(ptSituationElement);

                                ZonedDateTime endTime = ZonedDateTime.now().plusHours(TIME_LIMIT_HOURS);
                                if (expiration == null || expiration.isAfter(endTime)) {
                                    ptSituationElement.getValidityPeriods()
                                            .forEach(validity -> {
                                                if (validity.getEndTime() != null && validity.getEndTime().isAfter(endTime)) {
                                                    validity.setEndTime(endTime);
                                                }
                                            });

                                    getMetricsService().registerDataMapping(
                                            SiriDataType.SITUATION_EXCHANGE,
                                            datasetId,
                                            LIMIT_CLOSED_SX_MESSAGE_END_TIME,
                                            1
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private ZonedDateTime getLastEndTime(PtSituationElement situationElement) {
        List<HalfOpenTimestampOutputRangeStructure> validityPeriods = situationElement.getValidityPeriods();

        ZonedDateTime expiry = null;

        if (validityPeriods != null) {
            for (HalfOpenTimestampOutputRangeStructure validity : validityPeriods) {

                //Find latest validity
                if (expiry == null) {
                    expiry = validity.getEndTime();
                } else if (validity != null && validity.getEndTime().isAfter(expiry)) {
                    expiry = validity.getEndTime();
                }
            }
        }
        return expiry;
    }
}
