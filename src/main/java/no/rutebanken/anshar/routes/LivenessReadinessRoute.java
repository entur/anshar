package no.rutebanken.anshar.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String inboundPort;


    public LivenessReadinessRoute(String inboundPort) {
        this.inboundPort = inboundPort;
    }

    @Override
    public void configure() throws Exception {

        //Incoming notifications/deliveries
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
        ;
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/up")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
        ;
    }

}
