package no.rutebanken.anshar.subscription;

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
            for (SubscriptionSetup subscriptionSetup : subscriptionSetups) {
                subscriptionSetup.setAddress(inboundUrl);

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
}
