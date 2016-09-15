package no.rutebanken.anshar.routes.websocket;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.apache.camel.builder.RouteBuilder;
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

/*
        from("websocket://siri")
                .to("log:foo")
                .setBody(simple("${body}"))
                .to("websocket://siri")
        ;

        from("websocket://siri_vm")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createVMServiceDelivery(Vehicles.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_vm")
        ;

        from("websocket://siri_sx")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createSXServiceDelivery(Situations.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_sx")
        ;

        from("websocket://siri_et")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createETServiceDelivery(Journeys.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_et")
        ;

        from("websocket://siri_pt")
                .to("log:foo")
                .process(p -> {
                    p.getOut().setBody(SiriXml.toXml(factory.createPTServiceDelivery(ProductionTimetables.getAll())));
                    p.getOut().setHeader("Accept-Encoding", p.getIn().getHeader("Accept-Encoding"));
                    p.getOut().setHeader(WebsocketConstants.CONNECTION_KEY, p.getIn().getHeader(WebsocketConstants.CONNECTION_KEY));
                })
                .to("websocket://siri_pt")
        ;
*/
    }

}
