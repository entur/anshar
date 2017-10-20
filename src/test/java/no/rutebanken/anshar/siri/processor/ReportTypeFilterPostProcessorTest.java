package no.rutebanken.anshar.siri.processor;

import junit.framework.TestCase;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.ReportTypeFilterPostProcessor;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationNumber;
import uk.org.siri.siri20.WorkflowStatusEnumeration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ReportTypeFilterPostProcessorTest extends TestCase {

    private final String incidentReportType = "incident";
    private final String unknownReportType = "unknown";

    public void testReportTypeFilter() {
        List<PtSituationElement> sxList = new ArrayList<>();
        int incidentCount = 10;
        for (int i = 0; i < incidentCount; i++) {
            sxList.add(createPtSituation(incidentReportType));
        }
        int unknownCount = 5;
        for (int i = 0; i < unknownCount; i++) {
            sxList.add(createPtSituation(unknownReportType));
        }
        int nullCount = 5;
        for (int i = 0; i < nullCount; i++) {
            sxList.add(createPtSituation(null));
        }

        Siri s = new SiriObjectFactory(Instant.now()).createSXServiceDelivery(sxList);

        ReportTypeFilterPostProcessor processor = new ReportTypeFilterPostProcessor(incidentReportType);
        processor.process(s);

        assertNotNull(s.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations());
        List<PtSituationElement> elements = s.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements();
        assertNotNull(elements);

        assertEquals(incidentCount, elements.size());

        for (PtSituationElement element : elements) {
            assertEquals(incidentReportType, element.getReportType());
        }

    }

    private PtSituationElement createPtSituation(String reportType) {
        PtSituationElement element = new PtSituationElement();
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue("" + new Random().nextLong());
        element.setSituationNumber(situationNumber);
        element.setProgress(WorkflowStatusEnumeration.PUBLISHED);
        element.setReportType(reportType);
        return element;
    }
}