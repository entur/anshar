package no.rutebanken.anshar.subscription;

import com.google.common.base.Preconditions;
import no.rutebanken.anshar.routes.siri.*;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class SubscriptionConfig implements CamelContextAware {
    private static Logger logger = LoggerFactory.getLogger(SubscriptionConfig.class);

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Autowired
    private Config config;

    protected static CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Bean
    List<RouteBuilder> createSubscriptions() {
        List<RouteBuilder> builders = new ArrayList<>();
        if (config != null) {
            List<SubscriptionSetup> subscriptionSetups = config.getSubscriptions();
            logger.info("Initializing {} subscriptions", subscriptionSetups.size());
            Set<String> subscriptionIds = new HashSet<>();
            for (SubscriptionSetup subscriptionSetup : subscriptionSetups) {
                subscriptionSetup.setAddress(inboundUrl);

                if (!isValid(subscriptionSetup)) {
                    throw new ServiceConfigurationError("Configuration is not valid for subscription " + subscriptionSetup);
                }

                if (subscriptionIds.contains(subscriptionSetup.getSubscriptionId())) {
                    //Verify subscriptionId-uniqueness
                    throw new ServiceConfigurationError("SubscriptionIds are NOT unique for ID="+subscriptionSetup.getSubscriptionId());
                }
                subscriptionIds.add(subscriptionSetup.getSubscriptionId());

                if (subscriptionSetup.getVersion().equals("1.4")) {
                    if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
                        if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                            builders.add(new Siri20ToSiriWS14Subscription(subscriptionSetup));
                        } else {
                            builders.add(new Siri20ToSiriRS14Subscription(subscriptionSetup));
                        }
                    } else {
                        builders.add(new Siri20ToSiriWS14RequestResponse(subscriptionSetup));
                    }
                } else {
                    if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
                        builders.add(new Siri20ToSiriRS20Subscription(subscriptionSetup));
                    } else {
                        builders.add(new Siri20ToSiriRS20RequestResponse(subscriptionSetup));
                    }
                }
            }

            logger.info("Starting {} subscriptions", builders.size());
            for (RouteBuilder builder : builders) {
                try {
                    camelContext.addRoutes(builder);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            logger.error("Subscriptions not configured correctly - no subscriptions will be started");
        }
        return builders;
    }

    private boolean isValid(SubscriptionSetup s) {
        Preconditions.checkNotNull(s.getVendor(), "Vendor is not set");
        Preconditions.checkNotNull(s.getDatasetId(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getServiceType(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getSubscriptionType(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getSubscriptionMode(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getHeartbeatInterval(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getVersion(), "Version is not set");
        Preconditions.checkNotNull(s.getSubscriptionId(), "SubscriptionId is not set");
        Preconditions.checkNotNull(s.getRequestorRef(), "RequestorRef is not set");
        Preconditions.checkNotNull(s.getDurationOfSubscription(), "Duration is not set");
        Preconditions.checkNotNull(s.getUrlMap(), "UrlMap is not set");

        Map<RequestType, String> urlMap = s.getUrlMap();
        if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {
            if (SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_SITUATION_EXCHANGE), "GET_SITUATION_EXCHANGE-url is missing. " + s);
            } else if (SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_VEHICLE_MONITORING), "GET_VEHICLE_MONITORING-url is missing. " + s);
            }
        } else if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
            Preconditions.checkNotNull(urlMap.get(RequestType.SUBSCRIBE), "SUBSCRIBE-url is missing. " + s);
            Preconditions.checkNotNull(urlMap.get(RequestType.DELETE_SUBSCRIPTION), "DELETE_SUBSCRIPTION-url is missing. " + s);
        }

        return true;
    }
}
