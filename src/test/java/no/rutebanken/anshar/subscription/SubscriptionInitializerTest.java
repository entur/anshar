package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.*;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class SubscriptionInitializerTest {

    private final SubscriptionInitializer initializer = new SubscriptionInitializer();

    /*
     
     
      SIRI 2.0
     
     */
    @Test
    public void testSiri20RestRequestResponse() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE, SubscriptionSetup.ServiceType.REST, "2.0");

        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriRS20RequestResponse);
    }

    @Test
    public void testSiri20RestSubscribe() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.REST, "2.0");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriRS20Subscription);
    }

    @Test
    public void testSiri20RestSubscribeInitialDataSupply() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.REST, "2.0");
        subscriptionSetup.setDataSupplyRequestForInitialDelivery(true);
        subscriptionSetup.setVersion("2.0");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 2);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriRS20Subscription | routeBuilders.get(0) instanceof Siri20ToSiriRS20RequestResponse);
        assertTrue(routeBuilders.get(1) instanceof Siri20ToSiriRS20Subscription | routeBuilders.get(1) instanceof Siri20ToSiriRS20RequestResponse);
    }

    @Test
    public void testSiri20RestFetchedDelivery() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY, SubscriptionSetup.ServiceType.REST, "2.0");
        subscriptionSetup.setDataSupplyRequestForInitialDelivery(true);

        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 2);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriRS20Subscription | routeBuilders.get(0) instanceof Siri20ToSiriRS20RequestResponse);
        assertTrue(routeBuilders.get(1) instanceof Siri20ToSiriRS20Subscription | routeBuilders.get(1) instanceof Siri20ToSiriRS20RequestResponse);
    }

    @Test
    public void testSiri20SoapRequestResponse() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE, SubscriptionSetup.ServiceType.SOAP, "2.0");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS20RequestResponse);
    }
    @Test
    public void testSiri20SoapSubscribe() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.SOAP, "2.0");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS20Subscription);
    }

    @Test
    public void testSiri20SoapSubscribeInitialDataSupply() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.SOAP, "2.0");
        subscriptionSetup.setDataSupplyRequestForInitialDelivery(true);

        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 2);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS20Subscription | routeBuilders.get(0) instanceof Siri20ToSiriWS20RequestResponse);
        assertTrue(routeBuilders.get(1) instanceof Siri20ToSiriWS20Subscription | routeBuilders.get(1) instanceof Siri20ToSiriWS20RequestResponse);
    }

    @Test
    public void testSiri20SoapFetchedDelivery() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY, SubscriptionSetup.ServiceType.SOAP, "2.0");
        subscriptionSetup.setDataSupplyRequestForInitialDelivery(true);
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 2);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS20Subscription | routeBuilders.get(0) instanceof Siri20ToSiriWS20RequestResponse);
        assertTrue(routeBuilders.get(1) instanceof Siri20ToSiriWS20Subscription | routeBuilders.get(1) instanceof Siri20ToSiriWS20RequestResponse);
    }

    /*
      SIRI 1.4
     */

    @Test
    public void testSiri14RestSubscribe() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.REST, "1.4");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriRS14Subscription);
    }

    @Test
    public void testSiri14SoapRequestResponse() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE, SubscriptionSetup.ServiceType.SOAP, "1.4");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS14RequestResponse);
    }
    @Test
    public void testSiri14SoapSubscribe() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.SOAP, "1.4");
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 1);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS14Subscription);
    }

    @Test
    @Ignore(value = "Not yet supported - ignoring for now")
    public void testSiri14SoapSubscribeInitialDataSupply() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.SUBSCRIBE, SubscriptionSetup.ServiceType.SOAP, "1.4");
        subscriptionSetup.setDataSupplyRequestForInitialDelivery(true);

        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 2);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS14Subscription | routeBuilders.get(0) instanceof Siri20ToSiriWS14RequestResponse);
        assertTrue(routeBuilders.get(1) instanceof Siri20ToSiriWS14Subscription | routeBuilders.get(1) instanceof Siri20ToSiriWS14RequestResponse);
    }

    @Test
    public void testSiri14SoapFetchedDelivery() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup(SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY, SubscriptionSetup.ServiceType.SOAP, "1.4");
        subscriptionSetup.setDataSupplyRequestForInitialDelivery(true);
        List<RouteBuilder> routeBuilders = initializer.getRouteBuilders(subscriptionSetup);

        assertTrue(routeBuilders.size() == 2);
        assertTrue(routeBuilders.get(0) instanceof Siri20ToSiriWS14Subscription | routeBuilders.get(0) instanceof Siri20ToSiriWS14RequestResponse);
        assertTrue(routeBuilders.get(1) instanceof Siri20ToSiriWS14Subscription | routeBuilders.get(1) instanceof Siri20ToSiriWS14RequestResponse);
    }

    private SubscriptionSetup createSubscriptionSetup(SubscriptionSetup.SubscriptionMode requestResponse, SubscriptionSetup.ServiceType rest, String version) {
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionMode(requestResponse);
        subscriptionSetup.setServiceType(rest);
        subscriptionSetup.setVersion(version);
        return subscriptionSetup;
    }
}