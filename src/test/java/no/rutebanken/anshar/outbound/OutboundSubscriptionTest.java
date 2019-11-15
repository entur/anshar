package no.rutebanken.anshar.outbound;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.org.siri.siri20.SubscriptionContextStructure;
import uk.org.siri.siri20.SubscriptionRequest;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class OutboundSubscriptionTest {

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

    SubscriptionRequest getSubscriptionRequest(long heartbeatIntervalMillis) throws DatatypeConfigurationException {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        SubscriptionContextStructure context = new SubscriptionContextStructure();

        context.setHeartbeatInterval(DatatypeFactory.newInstance().newDuration(heartbeatIntervalMillis));

        subscriptionRequest.setSubscriptionContext(context);
        return subscriptionRequest;
    }
}
