package no.rutebanken.anshar.outbound;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SubscriptionContextStructure;
import uk.org.siri.siri21.SubscriptionRequest;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.stream.XMLStreamException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutboundSubscriptionTest extends SpringBootBaseTest {

    @Autowired
    ServerSubscriptionManager serverSubscriptionManager;


    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 10000;

    @Value("${anshar.outbound.heartbeatinterval.maximum}")
    private long maximumHeartbeatInterval = 300000;

    @Test
    public void testHeartbeatInterval() throws DatatypeConfigurationException {
        final long tooShortDurationInMilliSeconds = minimumHeartbeatInterval - 1;
        final long tooLongDurationInMilliSeconds = maximumHeartbeatInterval + 1;

        SubscriptionRequest subscriptionRequestWithTooShortInterval = getSubscriptionRequest(tooShortDurationInMilliSeconds);
        long heartbeatInterval = serverSubscriptionManager.getHeartbeatInterval(subscriptionRequestWithTooShortInterval);

        assertTrue(tooShortDurationInMilliSeconds < minimumHeartbeatInterval);
        assertEquals(heartbeatInterval, minimumHeartbeatInterval);


        SubscriptionRequest subscriptionRequestWithTooLongInterval = getSubscriptionRequest(tooLongDurationInMilliSeconds);
        heartbeatInterval = serverSubscriptionManager.getHeartbeatInterval(subscriptionRequestWithTooLongInterval);

        assertTrue(tooLongDurationInMilliSeconds > maximumHeartbeatInterval);
        assertEquals(heartbeatInterval, maximumHeartbeatInterval);
    }

    @Test
    public void testDuplicateSubscriptionIds() throws JAXBException, XMLStreamException {
        final String subscriptionId = "36dfa2d0-51d7-42fb-b828-44fc07684239";
        String sxSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.2790513Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>https://0.0.0.0/api/siri/sx/36dfa2d0-51d7-42fb-b828-44fc07684239</ConsumerAddress>\n" +
                "\t\t<RequestorRef>GAG-SX-36dfa2d0-51d7-42fb-b828-44fc07684239</RequestorRef>\n" +
                "\t\t<MessageIdentifier>52467cd2-e469-4f37-8841-27c3a9b57b63</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<SituationExchangeSubscriptionRequest>\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.2785096Z</InitialTerminationTime>\n" +
                "\t\t\t<SituationExchangeRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.2787087Z</RequestTimestamp>\n" +
                "\t\t\t</SituationExchangeRequest>\n" +
                "\t\t</SituationExchangeSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";

        String etSubscription = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<SubscriptionRequest>\n" +
                "\t\t<RequestTimestamp>2019-12-06T14:38:42.5012071Z</RequestTimestamp>\n" +
                "\t\t<ConsumerAddress>https://0.0.0.0/api/siri/et/36dfa2d0-51d7-42fb-b828-44fc07684239</ConsumerAddress>\n" +
                "\t\t<RequestorRef>GAG-ET-36dfa2d0-51d7-42fb-b828-44fc07684239</RequestorRef>\n" +
                "\t\t<MessageIdentifier>444dde0d-3663-4de9-aa94-56ee17d86ba9</MessageIdentifier>\n" +
                "\t\t<SubscriptionContext>\n" +
                "\t\t\t<HeartbeatInterval>PT60M</HeartbeatInterval>\n" +
                "\t\t</SubscriptionContext>\n" +
                "\t\t<EstimatedTimetableSubscriptionRequest>\n" +
                "\t\t\t<SubscriptionIdentifier>" + subscriptionId + "</SubscriptionIdentifier>\n" +
                "\t\t\t<InitialTerminationTime>2119-12-06T14:38:42.500977Z</InitialTerminationTime>\n" +
                "\t\t\t<EstimatedTimetableRequest>\n" +
                "\t\t\t\t<RequestTimestamp>2019-12-06T14:38:42.5011831Z</RequestTimestamp>\n" +
                "\t\t\t</EstimatedTimetableRequest>\n" +
                "\t\t</EstimatedTimetableSubscriptionRequest>\n" +
                "\t</SubscriptionRequest>\n" +
                "</Siri>";


        final Siri siriSX = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(sxSubscription).getSubscriptionRequest(), null, OutboundIdMappingPolicy.DEFAULT, null);
        final Siri siriET = serverSubscriptionManager.handleSubscriptionRequest(SiriXml.parseXml(etSubscription).getSubscriptionRequest(), null, OutboundIdMappingPolicy.DEFAULT, null);

        assertNotNull(siriSX);
        assertNotNull(siriET);

        assertTrue(siriSX.getSubscriptionResponse() != null);
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses() != null);
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses().get(0).isStatus() != null);
        assertTrue(siriSX.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

        assertTrue(siriET.getSubscriptionResponse() != null);
        assertTrue(siriET.getSubscriptionResponse().getResponseStatuses() != null);
        assertTrue(siriET.getSubscriptionResponse().getResponseStatuses().get(0).isStatus() != null);
        assertFalse(siriET.getSubscriptionResponse().getResponseStatuses().get(0).isStatus());

        serverSubscriptionManager.terminateSubscription(subscriptionId, true);
    }

    SubscriptionRequest getSubscriptionRequest(long heartbeatIntervalMillis) throws DatatypeConfigurationException {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        SubscriptionContextStructure context = new SubscriptionContextStructure();

        context.setHeartbeatInterval(DatatypeFactory.newInstance().newDuration(heartbeatIntervalMillis));

        subscriptionRequest.setSubscriptionContext(context);
        return subscriptionRequest;
    }
}
