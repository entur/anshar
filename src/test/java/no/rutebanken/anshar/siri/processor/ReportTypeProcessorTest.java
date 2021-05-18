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

package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.ReportTypeProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReportTypeProcessorTest extends SpringBootBaseTest {


    private SiriObjectFactory objectFactory;
    private ReportTypeProcessor processor;

    @BeforeEach
    public void init() {
        objectFactory = new SiriObjectFactory(Instant.now());
        processor = new ReportTypeProcessor("TST");
    }

    @Test
    public void testNullReportType() {
        Siri siri = createSiri(null);
        processor.process(siri);

        String reportType = resolveReportType(siri);
        assertEquals("incident", reportType);
    }

    @Test
    public void testEmptyReportType() {
        Siri siri = createSiri("");
        processor.process(siri);

        String reportType = resolveReportType(siri);
        assertEquals("incident", reportType);
    }

    @Test
    public void testGeneralReportType() {
        Siri siri = createSiri("general");
        processor.process(siri);

        String reportType = resolveReportType(siri);
        assertEquals("general", reportType);
    }

    @Test
    public void testIncidentReportType() {
        Siri siri = createSiri("incident");
        processor.process(siri);

        String reportType = resolveReportType(siri);
        assertEquals("incident", reportType);
    }

    @Test
    public void testUnknownReportType() {
        Siri siri = createSiri("unknown");
        processor.process(siri);

        String reportType = resolveReportType(siri);
        assertEquals("incident", reportType);
    }

    private String resolveReportType(Siri siri) {
        assertNotNull(siri);
        assertNotNull(siri.getServiceDelivery());
        assertNotNull(siri.getServiceDelivery().getSituationExchangeDeliveries());
        assertNotNull(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0));
        assertNotNull(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations());
        assertNotNull(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements());
        assertNotNull(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0));
        return siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).getReportType();
    }

    private Siri createSiri(String reportType) {
        Collection<PtSituationElement> sxElements = new ArrayList<>();
        PtSituationElement sx = new PtSituationElement();
        sx.setReportType(reportType);
        sxElements.add(sx);
        return objectFactory.createSXServiceDelivery(sxElements);
    }
}
