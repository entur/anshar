package no.rutebanken.anshar.validation.sx;

import no.rutebanken.anshar.routes.validation.validators.sx.AffectedRouteValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.NodeList;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.*;

public class AffectedRouteValidatorTest extends CustomValidatorTest {

    @Autowired
    private AffectedRouteValidator validator;

    @BeforeEach
    public void init() {
        validator.prepareTestData("NSR:Quay:1234");
    }

    @Test
    public void testEmptyRouteRef() throws Exception  {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Siri version=\"2.0\" xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\">" +
                "<ServiceDelivery>" +
                "<ResponseTimestamp>2019-07-11T12:31:36+02:00</ResponseTimestamp>" +
                "<ProducerRef>ENT</ProducerRef>" +
                "<SituationExchangeDelivery>" +
                "<ResponseTimestamp>2019-07-11T12:31:36+02:00</ResponseTimestamp>" +
                "<Situations>" +
                "<PtSituationElement>" +
                "<CreationTime>2019-07-11T14:31:20+02:00</CreationTime>" +
                "<ParticipantRef>TST</ParticipantRef>" +
                "<SituationNumber>TST:SituationNumber:1</SituationNumber>" +
                "<Source>" +
                "<SourceType>directReport</SourceType>" +
                "</Source>" +
                "<Progress>open</Progress>" +
                "<ValidityPeriod>" +
                "<StartTime>2019-01-01T00:00:00+02:00</StartTime>" +
                "</ValidityPeriod>" +
                "<UndefinedReason></UndefinedReason>" +
                "<Severity>normal</Severity>" +
                "<ReportType>general</ReportType>" +
                "<Summary xml:lang=\"NO\">smumary</Summary>" +
                "<Affects>" +
                "<Networks>" +
                "<AffectedNetwork>" +
                "<AffectedLine>" +
                "<LineRef>TST:Line:1234</LineRef>" +
                "<Routes>" +
                "<AffectedRoute>" +
                "<RouteRef>123</RouteRef>" +                /* <===   Tested value ===> */
                "<StopPoints>" +
                "<AffectedStopPoint>" +
                "<StopPointRef>NSR:Quay:1234</StopPointRef>" +
                "</AffectedStopPoint>" +
                "</StopPoints>" +
                "</AffectedRoute>" +
                "</Routes>" +
                "</AffectedLine>" +
                "</AffectedNetwork>" +
                "</Networks>" +
                "</Affects>" +
                "</PtSituationElement>" +
                "</Situations>" +
                "</SituationExchangeDelivery>" +
                "</ServiceDelivery>" +
                "</Siri>";

        NodeList nodes = getMatchingNodelist(createXmlDocument(xml), validator.getXpath());

        assertEquals(1, nodes.getLength());

        for (int i = 0; i < nodes.getLength(); i++) {
            ValidationEvent validationEvent = validator.isValid(nodes.item(i));
            assertNotNull(validationEvent);
        }
    }


    @Test
    public void testValidRouteRef() throws Exception  {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Siri version=\"2.0\" xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\">" +
                "<ServiceDelivery>" +
                "<ResponseTimestamp>2019-07-11T12:31:36+02:00</ResponseTimestamp>" +
                "<ProducerRef>ENT</ProducerRef>" +
                "<SituationExchangeDelivery>" +
                "<ResponseTimestamp>2019-07-11T12:31:36+02:00</ResponseTimestamp>" +
                "<Situations>" +
                "<PtSituationElement>" +
                "<CreationTime>2019-07-11T14:31:20+02:00</CreationTime>" +
                "<ParticipantRef>TST</ParticipantRef>" +
                "<SituationNumber>TST:SituationNumber:1</SituationNumber>" +
                "<Source>" +
                "<SourceType>directReport</SourceType>" +
                "</Source>" +
                "<Progress>open</Progress>" +
                "<ValidityPeriod>" +
                "<StartTime>2019-01-01T00:00:00+02:00</StartTime>" +
                "</ValidityPeriod>" +
                "<UndefinedReason></UndefinedReason>" +
                "<Severity>normal</Severity>" +
                "<ReportType>general</ReportType>" +
                "<Summary xml:lang=\"NO\">smumary</Summary>" +
                "<Affects>" +
                "<Networks>" +
                "<AffectedNetwork>" +
                "<AffectedLine>" +
                "<LineRef>TST:Line:1234</LineRef>" +
                "<Routes>" +
                "<AffectedRoute>" +
                "<RouteRef>TST:Route:123</RouteRef>" +                /* <===   Tested value ===> */
                "</AffectedRoute>" +
                "</Routes>" +
                "</AffectedLine>" +
                "</AffectedNetwork>" +
                "</Networks>" +
                "</Affects>" +
                "</PtSituationElement>" +
                "</Situations>" +
                "</SituationExchangeDelivery>" +
                "</ServiceDelivery>" +
                "</Siri>";

        NodeList nodes = getMatchingNodelist(createXmlDocument(xml), validator.getXpath());

        assertEquals(1, nodes.getLength());

        for (int i = 0; i < nodes.getLength(); i++) {
            ValidationEvent validationEvent = validator.isValid(nodes.item(i));
            assertNull(validationEvent);
        }
    }

}
