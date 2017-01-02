package no.rutebanken.anshar.routes.health;

import no.rutebanken.anshar.messages.collections.ExtendedHazelcastService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@Configuration
public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Autowired
    ExtendedHazelcastService extendedHazelcastService;

    public static boolean triggerRestart;

    @Override
    public void configure() throws Exception {

        //To avoid large stacktraces in the log when fetching data using browser
        from("jetty:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
        ;

        // Application is ready to accept traffic
        from("jetty:http://0.0.0.0:" + inboundPort + "/ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
        ;

        // Application is (still) alive and well
        from("jetty:http://0.0.0.0:" + inboundPort + "/up")
                //TODO: On error - POST to hubot
                // Ex: wget --post-data='{"source":"otp", "message":"Downloaded file is empty or not present. This makes OTP fail! Please check logs"}' http://hubot/hubot/say/
                .choice()
                .when(p -> triggerRestart)
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                    .setBody(constant("Restart requested"))
                    .log("Application triggered restart")
                .endChoice()
                .when(p -> !extendedHazelcastService.isAlive())
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                    .setBody(simple("Hazelcast is shut down"))
                    .log("Hazelcast is shut down")
                .endChoice()
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                    .setBody(simple("OK"))
                .end()
        ;
    }
}
