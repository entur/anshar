package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.builder.RouteBuilder;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.TerminateSubscriptionResponseStructure;

import javax.xml.bind.JAXBException;
import java.io.StringWriter;
import java.util.UUID;

public abstract class SiriSubscriptionRouteBuilder extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    NamespacePrefixMapper customNamespacePrefixMapper;
    SubscriptionSetup subscriptionSetup;
    String uniqueRouteName = UUID.randomUUID().toString();


    public String marshalSiriSubscriptionRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);

        return SiriXml.toXml(siri, customNamespacePrefixMapper);
    }

    public String marshalSiriTerminateSubscriptionRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

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

    void initShedulerRoute() {

        //Verify health periodically
        long healthCheckInterval = subscriptionSetup.getHeartbeatInterval().getSeconds() * 1000;

        from("quartz2://healthcheck" + uniqueRouteName + "?fireNow=true&trigger.repeatInterval=" + healthCheckInterval)
                .choice()
                .when(hasNotBeenStarted -> !SubscriptionManager.isSubscriptionRegistered(subscriptionSetup.getSubscriptionId()))
                    .choice()
                    .when(isActive -> subscriptionSetup.isActive())
                        .log("Starting subscription " + subscriptionSetup.getSubscriptionId())
                        .process(p -> SubscriptionManager.addPendingSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup))
                        .to("direct:start" + uniqueRouteName)
                    .endChoice()
                .endChoice()
                .when(isHealthy -> SubscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId()))
                    .log("Subscription is healthy " + subscriptionSetup.getSubscriptionId())
                    .choice()
                        .when(isDeactivated -> !subscriptionSetup.isActive())
                            .log("Subscription has been deactivated - cancelling")
                            .to("direct:cancel" + uniqueRouteName)
                        .endChoice()
                .endChoice()
                .otherwise()
                    .log("Subscription has died - terminating subscription " + subscriptionSetup.getSubscriptionId())
                    .to("direct:cancel" + uniqueRouteName)
                    .log("Auto-restarting subscription " + subscriptionSetup.getSubscriptionId())
                        //.log("Auto-restart ignored")
                    //.process(p -> subscriptionSetup.setActive(false))
                    .to("direct:start" + uniqueRouteName)
                .end();

    }
}
