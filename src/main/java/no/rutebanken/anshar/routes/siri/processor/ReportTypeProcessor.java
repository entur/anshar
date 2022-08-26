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

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ReportTypeEnumeration;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.SET_MISSING_REPORT_TYPE;

public class ReportTypeProcessor extends ValueAdapter implements PostProcessor {

    private static final ReportTypeEnumeration DEFAULT_REPORT_TYPE = ReportTypeEnumeration.INCIDENT;
    private static final Set<ReportTypeEnumeration> allowedReportTypes = Sets.newHashSet(
            DEFAULT_REPORT_TYPE,
            ReportTypeEnumeration.GENERAL);
    private final String datasetId;

    public ReportTypeProcessor(String datasetId) {
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
                            ReportTypeEnumeration reportType = ptSituationElement.getReportType();
                            if (reportType == null || !allowedReportTypes.contains(reportType)) {
                                ptSituationElement.setReportType(DEFAULT_REPORT_TYPE);
                                getMetricsService().registerDataMapping(
                                        SiriDataType.SITUATION_EXCHANGE,
                                        datasetId,
                                        SET_MISSING_REPORT_TYPE,
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
