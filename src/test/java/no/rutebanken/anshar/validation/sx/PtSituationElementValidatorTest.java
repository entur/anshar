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

package no.rutebanken.anshar.validation.sx;

import no.rutebanken.anshar.routes.validation.validators.sx.PtSituationElementValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PtSituationElementValidatorTest extends CustomValidatorTest {

    private static PtSituationElementValidator validator;
    private final String fieldName = "PtSituationElement";
    private String creationTime;
    private String participantRef;
    private String situationNumber;
    private String source;
    private String progress;
    private String validityPeriod;
    private String reportType;
    private String summary;
    private String affects;


    @BeforeAll
    public static void init() {
        validator = new PtSituationElementValidator();
    }

    @BeforeEach
    public void initTestValues() {
        creationTime = createXml("CreationTime", "1234");
        participantRef = createXml("ParticipantRef", "1234");
        situationNumber = createXml("SituationNumber", "1234");
        source = createXml("Source", "1234");
        progress = createXml("Progress", "1234");
        validityPeriod = createXml("ValidityPeriod", "1234");
        reportType = createXml("ReportType", "1234");
        summary = createXml("Summary", "1234");
        affects = createXml("Affects", "1234");
    }

    @Test
    public void testCompletePtSituation() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNull(validator.isValid(node), "Valid " + fieldName + " flagged as invalid");
    }

    @Test
    public void testMissingCreationTime() throws Exception {
        Node node = createXmlNode(mergeXml(null,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing creationTime flagged as valid");
    }

    @Test
    public void testMissingParticipantRef() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            null,
                                            situationNumber,
                                            source,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing participantRef flagged as valid");
    }

    @Test
    public void testMissingSituationNumber() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            null,
                                            source,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing situationNumber flagged as valid");
    }

    @Test
    public void testMissingSource() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            null,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing source flagged as valid");
    }

    @Test
    public void testMissingProgress() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            null,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing progress flagged as valid");
    }

    @Test
    public void testMissingValidityPeriod() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            progress,
                                            null,
                                            reportType,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing validityPeriod flagged as valid");
    }

    @Test
    public void testMissingReportType() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            progress,
                                            validityPeriod,
                                            null,
                                            summary,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing reportType flagged as valid");
    }

    @Test
    public void testMissingSummary() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            null,
                                            affects)
                                );

        assertNotNull(validator.isValid(node), "Missing summary flagged as valid");
    }

    @Test
    public void testMissingAffects() throws Exception {
        Node node = createXmlNode(mergeXml(creationTime,
                                            participantRef,
                                            situationNumber,
                                            source,
                                            progress,
                                            validityPeriod,
                                            reportType,
                                            summary,
                                            null)
                                );

        assertNotNull(validator.isValid(node), "Missing affects flagged as valid");
    }

}