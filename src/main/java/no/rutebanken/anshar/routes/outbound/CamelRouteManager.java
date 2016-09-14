package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;

@Service
public class CamelRouteManager implements CamelContextAware {
    protected static CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String addSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        this.camelContext.addRoutes(route);
        return route.getDefinition().getId();
    }

    public boolean stopAndRemoveSiriPushRoute(String routeId) throws Exception {
        this.getCamelContext().stopRoute(routeId);
        return this.camelContext.removeRoute(routeId);
    }


    public void executeSiriPushRoute(Siri payload, String routeName) throws JAXBException {
        String xml = SiriXml.toXml(payload);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, xml);
    }
}
