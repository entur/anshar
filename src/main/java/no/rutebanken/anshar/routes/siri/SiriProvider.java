package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.Journeys;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.Vehicles;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.entity.ContentType;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;
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

        //To avoid large stacktraces in the log when fething data using browser
        from("jetty:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/stats")
                .process(p-> {
                    p.getOut().setBody(SubscriptionManager.buildStats());
                })
        ;

        // Dataproviders
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/sx?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (SX)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    if (datasetId != null && !datasetId.isEmpty()) {
                        p.getOut().setBody(SiriXml.toXml(factory.createSXSiriObject(Situations.getAll(datasetId))));
                    } else {
                        p.getOut().setBody(SiriXml.toXml(factory.createSXSiriObject(Situations.getAll())));
                    }
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/vm?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (VM)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    if (datasetId != null && !datasetId.isEmpty()) {
                        p.getOut().setBody(SiriXml.toXml(factory.createVMSiriObject(Vehicles.getAll(datasetId))));
                    } else {
                        p.getOut().setBody(SiriXml.toXml(factory.createVMSiriObject(Vehicles.getAll())));
                    }
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/et?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (ET)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    if (datasetId != null && !datasetId.isEmpty()) {
                        p.getOut().setBody(SiriXml.toXml(factory.createETSiriObject(Journeys.getAll(datasetId))));
                    } else {
                        p.getOut().setBody(SiriXml.toXml(factory.createETSiriObject(Journeys.getAll())));
                    }

                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/pt?httpMethodRestrict=GET")
                .log("RequestTracer [${in.header.breadcrumbId}] Incoming request (PT)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("datasetId");
                    if (datasetId != null && !datasetId.isEmpty()) {
                        p.getOut().setBody(SiriXml.toXml(factory.createPTSiriObject(ProductionTimetables.getAll(datasetId))));
                    } else {
                        p.getOut().setBody(SiriXml.toXml(factory.createPTSiriObject(ProductionTimetables.getAll())));
                    }
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;

        from("direct:processResponse")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setHeader(Exchange.CONTENT_TYPE, constant(ContentType.create("text/xml", Charset.forName("UTF-8"))))
//                .choice()
//                    .when(header("Accept-Encoding").contains("gzip"))
//                        .setHeader(Exchange.CONTENT_ENCODING, simple("gzip"))
//                        .marshal().gzip()
//                    .endChoice()
//                .otherwise()
                    .marshal().string()
                .end()
                .log("RequestTracer [${in.header.breadcrumbId}] Outgoing response")
        ;
    }
}
