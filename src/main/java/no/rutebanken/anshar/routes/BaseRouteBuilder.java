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

package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.policy.InterruptibleHazelcastRoutePolicy;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spring.SpringRouteBuilder;

import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.routes.CamelRouteNames.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;
import static no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory.getCamelUrl;

/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends SpringRouteBuilder {

    protected final SubscriptionManager subscriptionManager;

    protected final AnsharConfiguration config;

    protected BaseRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
        this.config = config;
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

        boolean isLeader = isLeader(fromRouteId);
        log.debug("isActive: {}, isActivated {}, isLeader {}: {}", subscriptionSetup.isActive(), subscriptionManager.isActiveSubscription(subscriptionId), isLeader, subscriptionSetup);

        return (isLeader && subscriptionSetup.isActive() && subscriptionManager.isActiveSubscription(subscriptionId));
    }

    protected boolean isLeader(String routeId) {
        RouteContext routeContext = getContext().getRoute(routeId).getRouteContext();
        List<RoutePolicy> routePolicyList = routeContext.getRoutePolicyList();
        if (routePolicyList != null) {
            for (RoutePolicy routePolicy : routePolicyList) {
                if (routePolicy instanceof InterruptibleHazelcastRoutePolicy) {
                    return ((InterruptibleHazelcastRoutePolicy) (routePolicy)).isLeader();
                }
            }
        }
        return false;
    }

    protected void releaseLeadership(String routeId) {
        RouteContext routeContext = getContext().getRoute(routeId).getRouteContext();
        List<RoutePolicy> routePolicyList = routeContext.getRoutePolicyList();
        if (routePolicyList != null) {
            for (RoutePolicy routePolicy : routePolicyList) {
                if (routePolicy instanceof InterruptibleHazelcastRoutePolicy) {
                    ((InterruptibleHazelcastRoutePolicy) routePolicy).releaseLeadership();
                    log.info("Leadership released: {}", routeId);
                }
            }
        }
    }


    protected String getRequestUrl(SubscriptionSetup subscriptionSetup) throws ServiceNotSupportedException {
        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();
        String url;
        if (subscriptionSetup.getSubscriptionType() == SiriDataType.ESTIMATED_TIMETABLE) {
            url = urlMap.get(RequestType.GET_ESTIMATED_TIMETABLE);
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.VEHICLE_MONITORING) {
            url = urlMap.get(RequestType.GET_VEHICLE_MONITORING);
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.SITUATION_EXCHANGE) {
            url = urlMap.get(RequestType.GET_SITUATION_EXCHANGE);
        } else {
            throw new ServiceNotSupportedException();
        }
        return getCamelUrl(url);
    }

    protected String getSoapAction(SubscriptionSetup subscriptionSetup) throws ServiceNotSupportedException {

        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE &&
                subscriptionSetup.isDataSupplyRequestForInitialDelivery()) {
            return "DataSupplyRequest";
        }

        if (subscriptionSetup.getSubscriptionType() == SiriDataType.ESTIMATED_TIMETABLE) {
            return "GetEstimatedTimetableRequest";
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.VEHICLE_MONITORING) {
            return "GetVehicleMonitoring";
        } else if (subscriptionSetup.getSubscriptionType() == SiriDataType.SITUATION_EXCHANGE) {
            return "GetSituationExchange";
        } else {
            throw new ServiceNotSupportedException();
        }
    }

}
