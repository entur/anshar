package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.RemovePersonalInformationProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationSourceStructure;
import uk.org.siri.siri20.SituationSourceTypeEnumeration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RemovePersonalInformationProcessorTest {

    private SiriObjectFactory objectFactory = new SiriObjectFactory(Instant.now());
    RemovePersonalInformationProcessor processor = new RemovePersonalInformationProcessor();

    @Test
    public void testRemovePersonalInformation() {
        Collection<PtSituationElement> sxElements = new ArrayList<>();
        PtSituationElement sx = new PtSituationElement();
        SituationSourceStructure situationSource = new SituationSourceStructure();
        situationSource.setSourceType(SituationSourceTypeEnumeration.DIRECT_REPORT);
        situationSource.setPhone("11223344");
        situationSource.setEmail("test@test.com");
        NaturalLanguageStringStructure name = new NaturalLanguageStringStructure();
        name.setValue("Test Testesen");
        situationSource.setName(name);
        sx.setSource(situationSource);
        sxElements.add(sx);


        Siri siri = objectFactory.createSXServiceDelivery(sxElements);

        final List<PtSituationElement> ptSituationElements = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements();

        assertNotNull(ptSituationElements);
        assertNotNull(ptSituationElements.get(0));

        final SituationSourceStructure source = ptSituationElements.get(0).getSource();
        assertNotNull(source);
        assertEquals(SituationSourceTypeEnumeration.DIRECT_REPORT, source.getSourceType());
        assertNotNull(source.getName());
        assertNotNull(source.getPhone());
        assertNotNull(source.getEmail());


        processor.process(siri);

        final List<PtSituationElement> cleanedPtSituationElements = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements();

        assertNotNull(cleanedPtSituationElements);
        assertNotNull(cleanedPtSituationElements.get(0));

        final SituationSourceStructure cleanedSource = ptSituationElements.get(0).getSource();
        assertNotNull(cleanedSource);
        assertEquals(SituationSourceTypeEnumeration.DIRECT_REPORT, cleanedSource.getSourceType());
        assertNull(cleanedSource.getName());
        assertNull(cleanedSource.getPhone());
        assertNull(cleanedSource.getEmail());


    }

}
