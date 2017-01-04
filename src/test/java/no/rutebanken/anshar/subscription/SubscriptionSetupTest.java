package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.*;

import static junit.framework.TestCase.*;

public class SubscriptionSetupTest {

    private SubscriptionSetup setup_1;
    private SubscriptionSetup setup_2;

    @Before
    public void setUp() {
        HashMap<RequestType, String> urlMap_1 = new HashMap<>();
        urlMap_1.put(RequestType.SUBSCRIBE, "http://localhost:1234/subscribe");

        HashMap<RequestType, String> urlMap_2 = new HashMap<>();
        urlMap_2.putAll(urlMap_1);

        setup_1 = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                Duration.ofHours(1),
                "http://www.kolumbus.no/siri",
                urlMap_1,
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<ValueAdapter>(),
                new HashMap<Class, Set<Object>>(),
                new ArrayList<String>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds((long) 1000),
                true
        );

        setup_2 = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                Duration.ofHours(1),
                "http://www.kolumbus.no/siri",
                urlMap_2,
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<ValueAdapter>(),
                new HashMap<Class, Set<Object>>(),
                new ArrayList<String>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds((long) 1000),
                true
        );
    }

    @Test
    public void testSimpleEquals() {
        assertEquals(setup_1, setup_2);
    }

    @Test
    public void testEqualsUpdatedSubscriptionType() {
        assertEquals(setup_1, setup_2);
        setup_2.setSubscriptionType(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING);
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedAddress() {
        assertEquals(setup_1, setup_2);
        setup_2.setAddress("http://other.address");
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedHeartbeatInterval() {
        assertEquals(setup_1, setup_2);
        setup_2.setHeartbeatIntervalSeconds((int) (setup_1.getHeartbeatInterval().getSeconds() + 1));
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedNameSpace() {
        assertEquals(setup_1, setup_2);
        setup_2.setOperatorNamespace("http://other.operator.namespace");
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedInitialDuration() {
        assertEquals(setup_1, setup_2);
        setup_2.setDurationOfSubscriptionHours((int) (setup_1.getDurationOfSubscription().getSeconds()*2));
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedUrl() {
        assertEquals(setup_1, setup_2);
        Map<RequestType, String> urlMap = setup_2.getUrlMap();
        assertTrue("urlMap does not contain expected URL", urlMap.containsKey(RequestType.SUBSCRIBE));
        urlMap.put(RequestType.SUBSCRIBE, urlMap.get(RequestType.SUBSCRIBE) + "/updated");
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsAddedUrl() {
        assertEquals(setup_1, setup_2);
        Map<RequestType, String> urlMap = setup_2.getUrlMap();
        urlMap.put(RequestType.GET_VEHICLE_MONITORING, urlMap.get(RequestType.SUBSCRIBE) + "/vm");
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsAlteredSubscriptionIdIgnored() {
        assertFalse(setup_1.getSubscriptionId().equals(setup_2.getSubscriptionId()));
        assertEquals(setup_1, setup_2);
    }

}
