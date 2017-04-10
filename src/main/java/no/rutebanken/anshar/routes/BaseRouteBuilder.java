package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spring.SpringRouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static no.rutebanken.anshar.routes.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;
import static no.rutebanken.anshar.routes.siri.RouteHelper.getCamelUrl;

/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends SpringRouteBuilder {


    @Autowired
    protected SubscriptionManager subscriptionManager;

    protected BaseRouteBuilder(SubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void configure() throws Exception {
        errorHandler(transactionErrorHandler()
                .logExhausted(true)
                .logRetryStackTrace(true));
    }
    /**
     * Create a new singleton route definition from URI. Only one such route should be active throughout the cluster at any time.
     */
    protected RouteDefinition singletonFrom(String uri, String routeId) {
        return this.from(uri)
                .group(SINGLETON_ROUTE_DEFINITION_GROUP_NAME)
                .routeId(routeId)
                .autoStartup(true);
    }


    protected boolean requestData(String subscriptionId, String fromRouteId) {
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        boolean isLeader = ((HazelcastRoutePolicy) (getContext().getRoute(fromRouteId).getRouteContext().getRoutePolicyList().get(0))).isLeader();
        log.info("isActive: {}, isActivated {}, isLeader {}", subscriptionSetup.isActive(), subscriptionManager.isActiveSubscription(subscriptionId), isLeader);

        return (isLeader & subscriptionSetup.isActive() && subscriptionManager.isActiveSubscription(subscriptionId));
    }



    protected String getRequestUrl(SubscriptionSetup subscriptionSetup) throws ServiceNotSupportedException {
        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();
        String url;
        if (subscriptionSetup.getSubscriptionType() == SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE) {
            url = urlMap.get(RequestType.GET_ESTIMATED_TIMETABLE);
        } else if (subscriptionSetup.getSubscriptionType() == SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING) {
            url = urlMap.get(RequestType.GET_VEHICLE_MONITORING);
        } else if (subscriptionSetup.getSubscriptionType() == SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE) {
            url = urlMap.get(RequestType.GET_SITUATION_EXCHANGE);
        } else {
            throw new ServiceNotSupportedException();
        }
        return getCamelUrl(url);
    }

    protected String getSoapAction(SubscriptionSetup subscriptionSetup) throws ServiceNotSupportedException {

        if (subscriptionSetup.getSubscriptionType() == SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE) {
            return "GetEstimatedTimetableRequest";
        } else if (subscriptionSetup.getSubscriptionType() == SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING) {
            return "GetVehicleMonitoring";
        } else if (subscriptionSetup.getSubscriptionType() == SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE) {
            return "GetSituationExchange";
        } else {
            throw new ServiceNotSupportedException();
        }
    }

}
