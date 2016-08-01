package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.Journeys;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.Vehicles;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
        ;

        // Dataproviders
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/anshar/rest/sx?httpMethodRestrict=GET")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createSXSiriObject(Situations.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/anshar/rest/vm?httpMethodRestrict=GET")
                .to("log:VM-request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createVMSiriObject(Vehicles.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;

        from("netty4-http:http://0.0.0.0:" + inboundPort + "/anshar/rest/et?httpMethodRestrict=GET")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createETSiriObject(Journeys.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                })
                .to("direct:processResponse")
        ;

        from("direct:processResponse")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .choice()
                    .when(header("Accept-Encoding").contains("gzip"))
                        .setHeader("Content-Encoding", constant("gzip"))
                        .marshal().gzip()
                    .endChoice()
                .otherwise()
                    .marshal()
        ;
    }
}
