package no.rutebanken.anshar.routes.health;

import com.hazelcast.core.ISet;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.util.Set;

@Service
@Configuration
public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.healthcheck.hubot.url}")
    private String hubotUrl;

    @Value("${anshar.healthcheck.hubot.payload.source}")
    private String hubotSource;

    @Value("${anshar.healthcheck.hubot.payload.icon.fail}")
    private String hubotIconFail;

    @Value("${anshar.healthcheck.hubot.payload.message.fail}")
    private String hubotMessageFail;

    @Value("${anshar.healthcheck.hubot.payload.icon.success}")
    private String hubotIconSuccess;

    @Value("${anshar.healthcheck.hubot.payload.message.success}")
    private String hubotMessageSuccess;

    @Value("${anshar.healthcheck.hubot.payload.template}")
    private String hubotTemplate;

    @Value("${anshar.healthcheck.hubot.allowed.inactivity.minutes:10}")
    private int allowedInactivityMinutes;

    @Value("${anshar.healthcheck.hubot.start.time}")
    private String startMonitorTimeStr;
    private LocalTime startMonitorTime;

    @Value("${anshar.healthcheck.hubot.end.time}")
    private String endMonitorTimeStr;
    private LocalTime endMonitorTime;

    @Autowired
    @Qualifier("getUnhealthySubscriptionsSet")
    ISet<String> unhealthySubscriptionsAlreadyNotified;

    @Autowired
    HealthManager healthManager;

    @Autowired
    SubscriptionManager subscriptionManager;

    public static boolean triggerRestart;

    @PostConstruct
    private void init() {
        startMonitorTime = LocalTime.parse(startMonitorTimeStr);
        endMonitorTime = LocalTime.parse(endMonitorTimeStr);
    }

    @Override
    public void configure() throws Exception {

        //To avoid large stacktraces in the log when fetching data using browser
        from("jetty:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
                .routeId("health.favicon")
        ;

        // Application is ready to accept traffic
        from("jetty:http://0.0.0.0:" + inboundPort + "/ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
                .log("Server is ready")
                .routeId("health.ready")
        ;

        // Application is (still) alive and well
        from("jetty:http://0.0.0.0:" + inboundPort + "/up")
                .log("Checking if server is up")
                //TODO: On error - POST to hubot - Ex: wget --post-data='{"source":"otp", "message":"Downloaded file is empty or not present. This makes OTP fail! Please check logs"}' http://hubot/hubot/say/
                .choice()
                .when(p -> !healthManager.isHazelcastAlive())
                    .log("Hazelcast is shut down")
                    .setBody(simple("Hazelcast is shut down"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                .endChoice()
                .otherwise()
                    .log("Server is up")
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.up")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/healthy")
                .log("Checking if server is healthy")
                .choice()
                .when(p -> !healthManager.isReceivingData())
                    .process(p -> {
                        p.getOut().setBody("Server has not received data for " + healthManager.getSecondsSinceDataReceived() + " seconds.");
                    })
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                    .log("Server reports not receiving data")
                .endChoice()
                .otherwise()
                    .log("Server is healthy")
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.healthy")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/anshardata")
                .log("Checking if server is healthy")
                .choice()
                .when(p -> getAllUnhealthySubscriptions().isEmpty() && !unhealthySubscriptionsAlreadyNotified.isEmpty())
                    .process(p -> {
                        unhealthySubscriptionsAlreadyNotified.clear();
                        String message = hubotMessageSuccess;

                        if (LocalTime.now().isAfter(startMonitorTime) &&
                                LocalTime.now().isBefore(endMonitorTime)) {
                            String jsonPayload = "{" + MessageFormat.format(hubotTemplate, hubotSource, hubotIconSuccess, message) + "}";
                            p.getOut().setBody("{" + jsonPayload + "}");
                            p.getOut().setHeader("notify-target", "hubot");
                        } else {
                            p.getOut().setBody(message);
                            p.getOut().setHeader("notify-target", "log");
                        }
                    })
                    .log("Server is back to normal")
                    .to("direct:notify.hubot")
                .endChoice()
                .when(p -> getAllUnhealthySubscriptions() != null && !getAllUnhealthySubscriptions().isEmpty())
                    .process(p -> {
                        Set<String> unhealthySubscriptions = getAllUnhealthySubscriptions();

                        //Avoid notifying multiple times for same subscriptions
                        unhealthySubscriptions.removeAll(unhealthySubscriptionsAlreadyNotified);

                        //Keep
                        unhealthySubscriptionsAlreadyNotified.addAll(unhealthySubscriptions);

                        if (!unhealthySubscriptions.isEmpty()) {
                            String message = MessageFormat.format(hubotMessageFail, getAllUnhealthySubscriptions());

                            if (LocalTime.now().isAfter(startMonitorTime) &&
                                    LocalTime.now().isBefore(endMonitorTime)) {

                                String jsonPayload = "{" + MessageFormat.format(hubotTemplate, hubotSource, hubotIconFail, message) + "}";
                                p.getOut().setBody( jsonPayload );
                                p.getOut().setHeader("notify-target", "hubot");
                            } else {
                                p.getOut().setBody("Subscriptions not receiving data - NOT notifying hubot:" + message);
                                p.getOut().setHeader("notify-target", "log");
                            }
                        }
                    })
                    .log("Server is NOT receiving data")
                    .to("direct:notify.hubot")
                .endChoice()
                .otherwise()
                    .log("Server is receiving data")
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .endChoice()
                .routeId("health.data.received")
        ;
        from("direct:notify.hubot")
                .choice()
                .when(header("notify-target").isEqualTo("log"))
                    .to("log:health:" + getClass().getSimpleName() + "?showAll=false&multiline=false")
                .endChoice()
                .when(header("notify-target").isEqualTo("hubot"))
                    .to("log:health:" + getClass().getSimpleName() + "?showAll=false&multiline=false")
//                    .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.JSON_UTF_8))
//                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
//                    .to(hubotUrl)
                .endChoice()
        ;

    }

    private Set<String> getAllUnhealthySubscriptions() {
        return subscriptionManager.getAllUnhealthySubscriptions(allowedInactivityMinutes*60);
    }
}
