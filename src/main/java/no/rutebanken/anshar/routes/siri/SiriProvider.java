package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import org.apache.camel.builder.RouteBuilder;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
@Configuration
public class SiriProvider extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private ProductionTimetables productionTimetables;
    
    @Autowired
    private SiriObjectFactory siriObjectFactory;

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
                        response = siriObjectFactory.createSXServiceDelivery(situations.getAll(datasetId));
                    }  else if (requestorId != null && !requestorId.isEmpty()) {
                        response = siriObjectFactory.createSXServiceDelivery(situations.getAllUpdates(requestorId));
                    } else {
                        response = siriObjectFactory.createSXServiceDelivery(situations.getAll());
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
                        response = siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAll(datasetId));
                    }  else if (requestorId != null && !requestorId.isEmpty()) {
                        response = siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAllUpdates(requestorId));
                    } else {
                        response = siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAll());
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
                        response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAll(datasetId));
                    } else if (requestorId != null && !requestorId.isEmpty()) {
                        response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllUpdates(requestorId));
                    } else {
                        response = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAll());
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
                        response = siriObjectFactory.createPTServiceDelivery(productionTimetables.getAll(datasetId));
                    }  else if (requestorId != null && !requestorId.isEmpty()) {
                        response = siriObjectFactory.createPTServiceDelivery(productionTimetables.getAllUpdates(requestorId));
                    } else {
                        response = siriObjectFactory.createPTServiceDelivery(productionTimetables.getAll());
                    }
                    HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                    SiriXml.toXml(response, null, out.getOutputStream());
                })
                .log("RequestTracer - Request done (PT)")
        ;
    }
}
