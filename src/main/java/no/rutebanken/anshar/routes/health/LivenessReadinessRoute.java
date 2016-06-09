package no.rutebanken.anshar.routes.health;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

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
