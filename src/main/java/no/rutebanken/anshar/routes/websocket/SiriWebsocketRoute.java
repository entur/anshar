package no.rutebanken.anshar.routes.websocket;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import java.util.function.Function;

import static org.eclipse.jetty.http.HttpStatus.Code.BAD_REQUEST;

@Service
public class SiriWebsocketRoute extends RouteBuilder implements CamelContextAware {

    private CamelContext camelContext;

    public static final String SIRI_ET_TOPIC_ROUTE = "activemq:topic:anshar.outbound.estimated_timetable";

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    SiriObjectFactory siriObjectFactory;

    SiriHelper siriHelper;

    @Override
    public void configure() {

        siriHelper = new SiriHelper(siriObjectFactory);

        final String routeIdPrefix = "websocket.route.client.";

        from(SIRI_ET_TOPIC_ROUTE)
                .to("websocket://et?port={{anshar.websocket.port:9292}}");

        from("direct:send.ws.connect.response")
                .routeId(routeIdPrefix + "initial.response")
                .bean(metrics, "countOutgoingData(${body}, WEBSOCKET)")
                .to("direct:siri.transform.output")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                .split().tokenizeXML("Siri").streaming()
                .to("direct:map.jaxb.to.protobuf")
                .to("websocket://et?port={{anshar.websocket.port:9292}}");


        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri");

        // Route that handles initial data
        from("websocket://et?port={{anshar.websocket.port:9292}}")
                .routeId(routeIdPrefix + "connected")
                .choice()
                .when().xpath("/siri:Siri/siri:ServiceRequest/siri:EstimatedTimetableRequest", ns)
                    .wireTap("direct:process.initial.websocket.connection")
                .endChoice()
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(BAD_REQUEST))
                .end()
                .log("Connected ET-client");

        from("direct:process.initial.websocket.connection")
                .routeId(routeIdPrefix + "initialize")
                .log("Finding initial data for websocket-client")
                .process( p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());
                    try {
                        Siri siri = SiriXml.parseXml(p.getIn().getBody(String.class));
                        if (siri != null && siri.getServiceRequest() != null && siri.getServiceRequest().getEstimatedTimetableRequests() != null) {

                            Siri etServiceDelivery = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllMonitored());

                            p.getOut().setBody(etServiceDelivery);
                        }
                    } catch (Throwable t) {
                        p.getOut().setBody(null);
                    }

                })
                .log("Finding initial data for websocket-client - done")
                .choice()
                    .when(body().isNotNull())
                        .to("direct:send.ws.connect.response")
                        .log("Sending DataReadyNotification to indicate that initial delivery is finished.")
                        .setBody((Function<Exchange, ? extends Object>) (Function<Exchange, Object>) exchange -> siriObjectFactory.createDataReadyNotification())
                        .to("direct:send.ws.connect.response")
                .end();
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

