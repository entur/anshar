package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.entity.ContentType;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

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
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (SX)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    HttpServletResponse response = p.getOut().getBody(HttpServletResponse.class);
                    if (datasetId != null && !datasetId.isEmpty()) {
                        SiriXml.toXml(factory.createSXServiceDelivery(Situations.getAll(datasetId)), null, response.getOutputStream());
                    } else {
                        SiriXml.toXml(factory.createSXServiceDelivery(Situations.getAll()), null, response.getOutputStream());
                    }
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"));
                    p.getOut().setHeader(Exchange.CONTENT_TYPE, constant(ContentType.create("text/xml", Charset.forName("UTF-8"))));
                })
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/vm?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (VM)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    HttpServletResponse response = p.getOut().getBody(HttpServletResponse.class);
                    if (datasetId != null && !datasetId.isEmpty()) {
                        SiriXml.toXml(factory.createVMServiceDelivery(VehicleActivities.getAll(datasetId)), null, response.getOutputStream());
                    } else {
                        SiriXml.toXml(factory.createVMServiceDelivery(VehicleActivities.getAll()), null, response.getOutputStream());
                    }
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"));
                    p.getOut().setHeader(Exchange.CONTENT_TYPE, constant(ContentType.create("text/xml", Charset.forName("UTF-8"))));
                })
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/et?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (ET)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    HttpServletResponse response = p.getOut().getBody(HttpServletResponse.class);
                    if (datasetId != null && !datasetId.isEmpty()) {
                        SiriXml.toXml(factory.createETServiceDelivery(EstimatedTimetables.getAll(datasetId)), null, response.getOutputStream());
                    } else {
                        SiriXml.toXml(factory.createETServiceDelivery(EstimatedTimetables.getAll()), null, response.getOutputStream());
                    }

                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"));
                    p.getOut().setHeader(Exchange.CONTENT_TYPE, constant(ContentType.create("text/xml", Charset.forName("UTF-8"))));
                })
                .log("RequestTracer [${in.header.breadcrumbId}] Outgoing response (ET)")
        ;


        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/pt?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (PT)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    HttpServletResponse response = p.getOut().getBody(HttpServletResponse.class);
                    if (datasetId != null && !datasetId.isEmpty()) {
                        SiriXml.toXml(factory.createPTServiceDelivery(ProductionTimetables.getAll(datasetId)), null, response.getOutputStream());
                    } else {
                        SiriXml.toXml(factory.createPTServiceDelivery(ProductionTimetables.getAll()), null, response.getOutputStream());
                    }
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"));
                    p.getOut().setHeader(Exchange.CONTENT_TYPE, constant(ContentType.create("text/xml", Charset.forName("UTF-8"))));
                })
        ;

    }
}
