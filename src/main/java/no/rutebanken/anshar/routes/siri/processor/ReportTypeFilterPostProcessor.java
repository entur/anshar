package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;

import java.util.List;

public class ReportTypeFilterPostProcessor extends ValueAdapter implements PostProcessor {
    private final String reportTypeToKeep;

    public ReportTypeFilterPostProcessor(String reportTypeToKeep) {
        this.reportTypeToKeep = reportTypeToKeep;
    }


    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {
            List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
            if (situationExchangeDeliveries != null) {
                for (SituationExchangeDeliveryStructure deliveryStructure : situationExchangeDeliveries) {
                    SituationExchangeDeliveryStructure.Situations situations = deliveryStructure.getSituations();
                    if (situations != null && situations.getPtSituationElements() != null) {
                        situations.getPtSituationElements().removeIf(sit -> !reportTypeToKeep.equals(sit.getReportType()));
                    }
                }
            }
        }
    }
}
