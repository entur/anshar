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

import io.micrometer.core.instrument.Counter;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.MappingNames;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.InfoLinkStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SituationNumber;

import java.util.Arrays;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.processor.CleanupInfoLinksPostProcessor.normalizeUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CleanupInfoLinksPostProcessorTest extends SpringBootBaseTest {

    private static final String DATASET_ID = "TST";
    private static final String DATA_MAPPING_COUNTER = "app.anshar.data.mapping";

    @Autowired
    private PrometheusMetricsService metricsService;

    private final CleanupInfoLinksPostProcessor processor = new CleanupInfoLinksPostProcessor(DATASET_ID);

    @Test
    public void testNullUriIsDiscarded() {
        assertNull(normalizeUri(null));
    }

    @Test
    public void testEmptyUriIsDiscarded() {
        assertNull(normalizeUri(""));
    }

    @Test
    public void testBlankUriIsDiscarded() {
        assertNull(normalizeUri("   "));
    }

    @Test
    public void testMissingSchemeIsPrefixedWithHttps() {
        assertEquals("https://www.entur.no/info", normalizeUri("www.entur.no/info"));
    }

    @Test
    public void testSurroundingWhitespaceIsTrimmedBeforePrefixing() {
        assertEquals("https://www.entur.no", normalizeUri("  www.entur.no\n"));
    }

    @Test
    public void testProtocolRelativeUriIsPrefixedWithHttps() {
        assertEquals("https://www.entur.no/x", normalizeUri("//www.entur.no/x"));
    }

    @Test
    public void testHttpsUriIsKeptAsIs() {
        assertEquals("https://www.entur.no", normalizeUri("https://www.entur.no"));
    }

    @Test
    public void testHttpUriIsKeptAsIsAndNotUpgraded() {
        assertEquals("http://www.entur.no", normalizeUri("http://www.entur.no"));
    }

    @Test
    public void testSchemeMatchIsCaseInsensitive() {
        assertEquals("HTTPS://www.entur.no", normalizeUri("HTTPS://www.entur.no"));
    }

    @Test
    public void testUnparseableUriIsDiscarded() {
        assertNull(normalizeUri("htt p://www.entur.no"));
    }

    @Test
    public void testNonHttpSchemeIsDiscarded() {
        assertNull(normalizeUri("ftp://www.entur.no"));
    }

    @Test
    public void testPathWithoutHostnameIsDiscarded() {
        assertNull(normalizeUri("/info/2024"));
    }

    @Test
    public void testSchemelessValueIsAssumedToStartWithHostname() {
        // Known limitation: a single-word value becomes a - possibly nonexistent - hostname
        assertEquals("https://info", normalizeUri("info"));
    }

    @Test
    public void testInvalidInfoLinksAreRemovedFromSituation() {
        PtSituationElement situation = createSituation("www.entur.no", null, "  ");

        processor.process(createSiri(situation));

        List<InfoLinkStructure> infoLinks = situation.getInfoLinks().getInfoLinks();
        assertEquals(1, infoLinks.size());
        assertEquals("https://www.entur.no", infoLinks.get(0).getUri());
    }

    @Test
    public void testInfoLinksIsRemovedWhenNoValidLinksRemain() {
        PtSituationElement situation = createSituation(null, "");

        processor.process(createSiri(situation));

        assertNull(situation.getInfoLinks());
    }

    @Test
    public void testEmptyInfoLinksIsRemoved() {
        PtSituationElement situation = createSituation();

        processor.process(createSiri(situation));

        assertNull(situation.getInfoLinks());
    }

    @Test
    public void testSituationWithoutInfoLinksIsLeftUntouched() {
        PtSituationElement situation = new PtSituationElement();
        situation.setSituationNumber(createSituationNumber());

        processor.process(createSiri(situation));

        assertNull(situation.getInfoLinks());
    }

    @Test
    public void testRemovedInfoLinksAreCountedAsDataMapping() {
        double countBefore = getDataMappingCount(MappingNames.REMOVE_INVALID_INFO_LINK);

        processor.process(createSiri(createSituation(null, "", "https://www.entur.no")));

        assertEquals(countBefore + 2, getDataMappingCount(MappingNames.REMOVE_INVALID_INFO_LINK));
    }

    @Test
    public void testAppendedSchemeIsCountedAsDataMapping() {
        double countBefore = getDataMappingCount(MappingNames.APPEND_SCHEME_TO_INFO_LINK);

        processor.process(createSiri(createSituation("www.entur.no", "https://www.entur.no")));

        assertEquals(countBefore + 1, getDataMappingCount(MappingNames.APPEND_SCHEME_TO_INFO_LINK));
    }

    private double getDataMappingCount(MappingNames mappingName) {
        Counter counter = metricsService.find(DATA_MAPPING_COUNTER)
                .tag("dataType", SiriDataType.SITUATION_EXCHANGE.name())
                .tag("agency", DATASET_ID)
                .tag("mappingId", mappingName.name())
                .counter();

        return counter != null ? counter.count() : 0;
    }

    private PtSituationElement createSituation(String... uris) {
        PtSituationElement situation = new PtSituationElement();
        situation.setSituationNumber(createSituationNumber());

        PtSituationElement.InfoLinks infoLinks = new PtSituationElement.InfoLinks();
        for (String uri : uris) {
            InfoLinkStructure infoLink = new InfoLinkStructure();
            infoLink.setUri(uri);
            infoLinks.getInfoLinks().add(infoLink);
        }
        situation.setInfoLinks(infoLinks);
        return situation;
    }

    private SituationNumber createSituationNumber() {
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue("TST:SituationNumber:1234");
        return situationNumber;
    }

    private Siri createSiri(PtSituationElement... situations) {
        SituationExchangeDeliveryStructure.Situations situationsStructure = new SituationExchangeDeliveryStructure.Situations();
        situationsStructure.getPtSituationElements().addAll(Arrays.asList(situations));

        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        delivery.setSituations(situationsStructure);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getSituationExchangeDeliveries().add(delivery);

        Siri siri = new Siri();
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}