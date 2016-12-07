package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.rutebanken.siri20.util.SiriXml;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import java.io.StringWriter;

public class RouteHelper {

    private NamespacePrefixMapper customNamespacePrefixMapper;
    private SubscriptionSetup subscriptionSetup;



    public RouteHelper(SubscriptionSetup subscriptionSetup, NamespacePrefixMapper customNamespacePrefixMapper) {
        this.subscriptionSetup = subscriptionSetup;
        this.customNamespacePrefixMapper = customNamespacePrefixMapper;
    }

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
}
