package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import org.apache.camel.builder.RouteBuilder;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class SiriProvider extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    private SiriObjectFactory factory;
    private SiriHandler handler;

    public SiriProvider() {
        factory = new SiriObjectFactory();
        handler = new SiriHandler();
    }

    @Override
    public void configure() throws Exception {


        // Dataproviders
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/sx?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (SX)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");

                    Siri response;
                    if (datasetId != null && !datasetId.isEmpty()) {
                        response = factory.createSXServiceDelivery(Situations.getAll(datasetId));
                    }  else if (requestorId != null && !requestorId.isEmpty()) {
                        response = factory.createSXServiceDelivery(Situations.getAllUpdates(requestorId));
                    } else {
                        response = factory.createSXServiceDelivery(Situations.getAll());
                    }
                    HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                    SiriXml.toXml(response, null, out.getOutputStream());
                })
                .log("RequestTracer - Request done (SX)")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/vm?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (VM)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");

                    Siri response;
                    if (datasetId != null && !datasetId.isEmpty()) {
                        response = factory.createVMServiceDelivery(VehicleActivities.getAll(datasetId));
                    }  else if (requestorId != null && !requestorId.isEmpty()) {
                        response = factory.createVMServiceDelivery(VehicleActivities.getAllUpdates(requestorId));
                    } else {
                        response = factory.createVMServiceDelivery(VehicleActivities.getAll());
                    }
                    HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                    SiriXml.toXml(response, null, out.getOutputStream());
                })
                .log("RequestTracer - Request done (VM)")
        ;


        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/et?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (ET)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");

                    Siri response;
                    if (datasetId != null && !datasetId.isEmpty()) {
                        response = factory.createETServiceDelivery(EstimatedTimetables.getAll(datasetId));
                    } else if (requestorId != null && !requestorId.isEmpty()) {
                        response = factory.createETServiceDelivery(EstimatedTimetables.getAllUpdates(requestorId));
                    } else {
                        response = factory.createETServiceDelivery(EstimatedTimetables.getAll());
                    }
                    HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                    SiriXml.toXml(response, null, out.getOutputStream());
                })
                .log("RequestTracer - Request done (ET)")
        ;


        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/pt?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (PT)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    String requestorId = request.getParameter("requestorId");
                    Siri response;
                    if (datasetId != null && !datasetId.isEmpty()) {
                        response = factory.createPTServiceDelivery(ProductionTimetables.getAll(datasetId));
                    }  else if (requestorId != null && !requestorId.isEmpty()) {
                        response = factory.createPTServiceDelivery(ProductionTimetables.getAllUpdates(requestorId));
                    } else {
                        response = factory.createPTServiceDelivery(ProductionTimetables.getAll());
                    }
                    HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                    SiriXml.toXml(response, null, out.getOutputStream());
                })
                .log("RequestTracer - Request done (PT)")
        ;
    }
}
