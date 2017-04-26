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

    public static String getCamelUrl(String url) {
        if (url != null && url.startsWith("https4://")) {
            return url;
        }
        return "http4://" + url;
    }

    public RouteHelper(SubscriptionSetup subscriptionSetup, NamespacePrefixMapper customNamespacePrefixMapper) {
        this.subscriptionSetup = subscriptionSetup;
        this.customNamespacePrefixMapper = customNamespacePrefixMapper;
    }


    public RouteHelper(SubscriptionSetup subscriptionSetup) {
        this.subscriptionSetup = subscriptionSetup;
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


    private Boolean allData = Boolean.TRUE;

    /*
     * Called dynamically from camel-routes
     *
     * Creates ServiceRequest or DataSupplyRequest based on subscription type
     */
    public String marshalSiriRequest() throws JAXBException {
        Siri request = null;
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY) {
            request = SiriObjectFactory.createDataSupplyRequest(subscriptionSetup, allData);
            allData = Boolean.FALSE;
        } else {
            request = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        }
        return SiriXml.toXml(request);
    }

}
