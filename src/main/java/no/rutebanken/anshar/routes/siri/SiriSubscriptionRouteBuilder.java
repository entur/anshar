package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.builder.RouteBuilder;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionResponseStructure;
import uk.org.siri.siri20.TerminateSubscriptionResponseStructure;

import javax.xml.bind.JAXBException;
import java.io.StringWriter;
import java.util.UUID;

public abstract class SiriSubscriptionRouteBuilder extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    NamespacePrefixMapper customNamespacePrefixMapper;
    SubscriptionSetup subscriptionSetup;
    String uniqueRouteName = UUID.randomUUID().toString();

    /*
     * Called dynamically from camel-routes
     */
    public String marshalSiriSubscriptionRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);

        return SiriXml.toXml(siri, customNamespacePrefixMapper);
    }

    /*
     * Called dynamically from camel-routes
     */
    public String marshalSiriTerminateSubscriptionRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

        return SiriXml.toXml(siri, customNamespacePrefixMapper);
    }

    /*
     * Called dynamically from camel-routes
     */
    public String marshalSiriCheckStatusRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createCheckStatusRequest(subscriptionSetup);

        return SiriXml.toXml(siri, customNamespacePrefixMapper);
    }


    Siri handleSiriResponse(String xml) {
        try {
            Siri siri = SiriXml.parseXml(xml);

            if (siri.getTerminateSubscriptionResponse() != null) {
                TerminateSubscriptionResponseStructure response = siri.getTerminateSubscriptionResponse();
                response.getTerminationResponseStatuses().forEach(s -> {
                    boolean removed = SubscriptionManager.removeSubscription(s.getSubscriptionRef().getValue());
                    logger.info("Subscription " + s.getSubscriptionRef().getValue() + " terminated: " + removed);
                });
            }
            return siri;
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

    void handleSubscriptionResponse(SubscriptionResponseStructure response, String responseCode) {
        if (response.getResponseStatuses().isEmpty()) {
            if ("200".equals(responseCode)) {
                SubscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
            }
        } else {
            response.getResponseStatuses().forEach(s -> {
                if (s.isStatus() != null && s.isStatus()) {
                    SubscriptionManager.addSubscription(s.getSubscriptionRef().getValue(), subscriptionSetup);
                } else if (s.getErrorCondition() != null) {
                    logger.error("Error starting subscription:  {}", (s.getErrorCondition().getDescription() != null ? s.getErrorCondition().getDescription().getValue():""));
                    //Removing - will trigger new attempt
                    SubscriptionManager.removeSubscription(s.getSubscriptionRef().getValue());
                } else {
                    SubscriptionManager.addSubscription(s.getSubscriptionRef().getValue(), subscriptionSetup);
                }
            });
        }
    }
    void initShedulerRoute() {

        //Verify health periodically
        long healthCheckInterval = 30000;

        from("quartz2://healthcheck" + uniqueRouteName + "?fireNow=true&trigger.repeatInterval=" + healthCheckInterval)
                .choice()
                .when(hasNotBeenStarted -> !SubscriptionManager.isSubscriptionRegistered(subscriptionSetup.getSubscriptionId()))
                    .choice()
                    .when(isActive -> subscriptionSetup.isActive())
                        .log("Starting subscription " + subscriptionSetup.toString())
                        .process(p -> SubscriptionManager.addPendingSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup))
                        .to("direct:start" + uniqueRouteName)
                    .endChoice()
                .endChoice()
                .when(isHealthy -> SubscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId()))
                    //.log("Subscription is healthy " + subscriptionSetup.toString())
                    .choice()
                        .when(isDeactivated -> !subscriptionSetup.isActive())
                            .log("Subscription has been deactivated - cancelling")
                            .to("direct:cancel" + uniqueRouteName)
                            .process(p -> SubscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId()))
                        .endChoice()
                .endChoice()
                .otherwise()
                    .log("Subscription has died - terminating subscription " + subscriptionSetup.toString())
                    .to("direct:cancel" + uniqueRouteName)
                .end();

    }
}
