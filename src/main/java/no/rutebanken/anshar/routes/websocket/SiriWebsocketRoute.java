package no.rutebanken.anshar.routes.websocket;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.WebsocketConstants;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import static org.eclipse.jetty.http.HttpStatus.Code.BAD_REQUEST;

@Service
public class SiriWebsocketRoute extends RouteBuilder implements CamelContextAware {

    private CamelContext camelContext;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    SiriObjectFactory siriObjectFactory;

    @Override
    public void configure() throws Exception {


        // Handling changes sent to all websocket-clients
        from("activemq:topic:anshar.outbound.estimated_timetable")
                .routeId("distribute.to.websocket.estimated_timetable")
                .to("websocket://et?sendToAll=true")
                .log("Changes sent - ET");

        // Route that handles initial data
        from("websocket://et")
                .process( p -> {
                    try {
                        Siri siri = SiriXml.parseXml(p.getIn().getBody(String.class));
                        if (siri != null && siri.getServiceRequest() != null && siri.getServiceRequest().getEstimatedTimetableRequests() != null) {

                            p.getOut().setBody(siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllMonitored()));
                        }
                    } catch (Throwable t) {
                        p.getOut().setBody(null);
                        p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader("websocket.connectionKey"));
                        p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, BAD_REQUEST);
                    }
                })
                .choice()
                .when(body().isNotNull())
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .to("activemq:topic:anshar.outbound.estimated_timetable")
                .end()
                .log("Connected ET-client");

    }

    @OnWebSocketConnect
    private void sendAll() {
        System.err.println("Initializing WS");
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}

