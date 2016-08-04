package no.rutebanken.anshar.routes.websocket;

import no.rutebanken.anshar.messages.Journeys;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.Vehicles;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.websocket.*;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    private SiriObjectFactory factory;

    public WebSocketRoute() {
        this.factory = new SiriObjectFactory();
    }

    @Override
    public void configure() throws Exception {


        from("websocket://siri")
                .to("log:foo")
                .setBody(simple("${body}"))
                .to("websocket://siri")
        ;

        from("websocket://siri_vm")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createVMSiriObject(Vehicles.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_vm")
        ;

        from("websocket://siri_sx")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createSXSiriObject(Situations.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_sx")
        ;

        from("websocket://siri_et")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createETSiriObject(Journeys.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_et")
        ;

        from("websocket://siri_pt")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createPTSiriObject(ProductionTimetables.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_pt")
        ;

    }

}
