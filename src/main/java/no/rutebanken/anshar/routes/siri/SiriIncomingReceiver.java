package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SiriIncomingReceiver extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    static final String QUEUE_PREFIX              = "anshar.siri";
    static final String TRANSFORM_QUEUE           = QUEUE_PREFIX + ".transform";
    static final String ROUTER_QUEUE              = QUEUE_PREFIX + ".router";
    static final String DEFAULT_PROCESSOR_QUEUE   = QUEUE_PREFIX + ".process";
    static final String PUSH_UPDATES_QUEUE        = QUEUE_PREFIX + ".push";
    static final String SITUATION_EXCHANGE_QUEUE  = DEFAULT_PROCESSOR_QUEUE + ".sx";
    static final String VEHICLE_MONITORING_QUEUE  = DEFAULT_PROCESSOR_QUEUE + ".vm";
    static final String ESTIMATED_TIMETABLE_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".et";
    static final String PRODUCTION_TIMETABLE_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".pt";
    static final String HEARTBEAT_QUEUE           = DEFAULT_PROCESSOR_QUEUE + ".heartbeat";

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    private SiriObjectFactory factory;
    private SiriHandler handler;

    public SiriIncomingReceiver() {
        factory = new SiriObjectFactory();
        handler = new SiriHandler();
    }

    @Override
    public void configure() throws Exception {

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        //Incoming notifications/deliveries
        from("jetty:http://0.0.0.0:" + inboundPort + "?matchOnUriPrefix=true&httpMethodRestrict=POST")
                .setHeader("anshar.message.id", constant(UUID.randomUUID().toString()))
                .to("activemq:queue:" + TRANSFORM_QUEUE + "?disableReplyTo=true")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("202"))
                .setBody(constant(null))
        ;

        from("activemq:queue:" + TRANSFORM_QUEUE + "?asyncConsumer=true")
                .choice()
                    .when(p -> (logger.isTraceEnabled()))
                        .to("file:" + incomingLogDirectory)
                    .endChoice()
                .end()
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .to("activemq:queue:" + ROUTER_QUEUE)
        ;

        from("activemq:queue:" + ROUTER_QUEUE + "?asyncConsumer=true")
                .choice()
                .when().xpath("/siri:Siri/siri:HeartbeatNotification", ns)
                    .to("activemq:queue:" + HEARTBEAT_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery", ns)
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("activemq:queue:" + SITUATION_EXCHANGE_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery", ns)
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("activemq:queue:" + VEHICLE_MONITORING_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery", ns)
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("activemq:queue:" + ESTIMATED_TIMETABLE_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:ProductionTimetableDelivery", ns)
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("activemq:queue:" + PRODUCTION_TIMETABLE_QUEUE)
                .endChoice()
                .otherwise()
                    .to("activemq:queue:" + DEFAULT_PROCESSOR_QUEUE)
                .end()
                .routeId("anshar.activemq.route")
        ;


        from("activemq:queue:" + DEFAULT_PROCESSOR_QUEUE + "?asyncConsumer=true")
                .to("log:processor:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
        ;

        from("activemq:queue:" + HEARTBEAT_QUEUE + "?asyncConsumer=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
        ;

        from("activemq:queue:" + SITUATION_EXCHANGE_QUEUE + "?asyncConsumer=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_sx?sendToAll=true")
        ;

        from("activemq:queue:" + VEHICLE_MONITORING_QUEUE + "?asyncConsumer=true")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_vm?sendToAll=true")
        ;

        from("activemq:queue:" + ESTIMATED_TIMETABLE_QUEUE + "?asyncConsumer=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);

                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_et?sendToAll=true")
        ;


        from("activemq:queue:" + PRODUCTION_TIMETABLE_QUEUE + "?asyncConsumer=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);

                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_pt?sendToAll=true")
        ;

    }

    private String getSubscriptionIdFromPath(String path) {
        if (incomingPathPattern.startsWith("/")) {
            if (!path.startsWith("/")) {
                path = "/"+path;
            }
        } else {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }


        logger.trace("Incoming path {}", path);
        Map<String, String> values = calculatePathVariableMap(path);
        logger.trace("Mapped values {}", values);

        return values.get("subscriptionId");
    }

    private Map<String, String> calculatePathVariableMap(String path) {
        String[] parameters = path.split("/");
        String[] parameterNames = incomingPathPattern.split("/");

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {

            String value = (parameters.length > i ? parameters[i] : null);

            if (parameterNames[i].startsWith("{")) {
                parameterNames[i] = parameterNames[i].substring(1);
            }
            if (parameterNames[i].endsWith("}")) {
                parameterNames[i] = parameterNames[i].substring(0, parameterNames[i].lastIndexOf("}"));
            }

            values.put(parameterNames[i], value);
        }

        return values;
    }

}
