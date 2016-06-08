package no.rutebanken.anshar.routes;

import org.apache.camel.builder.RouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.TerminateSubscriptionResponseStructure;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.UUID;

public abstract class SiriSubscriptionRouteBuilder extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private JAXBContext jaxbContext;
    Marshaller jaxbMarshaller;
    Unmarshaller jaxbUnmarshaller;
    SubscriptionSetup subscriptionSetup;
    String uniqueRouteName = UUID.randomUUID().toString();

    SiriSubscriptionRouteBuilder() {
        try {
            jaxbContext = JAXBContext.newInstance(Siri.class);
            jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            e.printStackTrace();
        }

    }



    public String marshalSiriSubscriptionRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);

        jaxbMarshaller.marshal(siri, sw);
        return sw.toString();
    }

    public String marshalSiriTerminateSubscriptionRequest() throws JAXBException {
        StringWriter sw = new StringWriter();

        Siri siri = SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

        jaxbMarshaller.marshal(siri, sw);

        return sw.toString();
    }


    Siri handleSiriResponse(String xml) {
        try {
            Siri siri = (Siri) jaxbUnmarshaller.unmarshal(new StringReader(xml));

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
                .endChoice()
                .otherwise()
                    .log("Subscription has died - terminating subscription " + subscriptionSetup.getSubscriptionId())
                    .to("direct:cancel" + uniqueRouteName)
                    .log("Auto-restarting subscription " + subscriptionSetup.getSubscriptionId())
                    .log("Auto-restart ignored")
                    .process(p -> subscriptionSetup.setActive(false))
                    //.to("direct:start" + uniqueRouteName)
                .end();

    }
}
