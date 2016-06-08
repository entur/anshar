package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.messages.Journeys;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.Vehicles;
import no.rutebanken.anshar.routes.handlers.SiriHandler;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Configuration
public class SiriIncomingReceiver extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String QUEUE_PREFIX              = "anshar.siri";
    private final String TRANSFORM_QUEUE           = QUEUE_PREFIX + ".transform";
    private final String ROUTER_QUEUE              = QUEUE_PREFIX + ".router";
    private final String DEFAULT_PROCESSOR_QUEUE   = QUEUE_PREFIX + ".process";
    private final String SITUATION_EXCHANGE_QUEUE  = DEFAULT_PROCESSOR_QUEUE + ".sx";
    private final String VEHICLE_MONITORING_QUEUE  = DEFAULT_PROCESSOR_QUEUE + ".vm";
    private final String ESTIMATED_TIMETABLE_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".et";
    private final String HEARTBEAT_QUEUE           = DEFAULT_PROCESSOR_QUEUE + ".heartbeat";

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    public SiriIncomingReceiver() {}

    @Override
    public void configure() throws Exception {


        //Incoming notifications/deliveries
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/rest/sx")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .process(p -> {
                    try {
                        p.getOut().setBody(SiriObjectFactory.toXml(SiriObjectFactory.createSXSiriObject(Situations.getAll())));
                    } catch (JAXBException e1) {
                        e1.printStackTrace();
                    }
                })
        ;
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/rest/vm")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .process(p -> {
                    try {
                        p.getOut().setBody(SiriObjectFactory.toXml(SiriObjectFactory.createVMSiriObject(Vehicles.getAll())));
                    } catch (JAXBException e1) {
                        e1.printStackTrace();
                    }
                })
        ;
        from("netty4-http:http://0.0.0.0:" + inboundPort + "/rest/et")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .process(p -> {
                    try {
                        p.getOut().setBody(SiriObjectFactory.toXml(SiriObjectFactory.createETSiriObject(Journeys.getAll())));
                    } catch (JAXBException e1) {
                        e1.printStackTrace();
                    }
                })
        ;


        //Incoming notifications/deliveries
        from("netty4-http:http://0.0.0.0:" + inboundPort + "?matchOnUriPrefix=true")
                .setHeader("anshar.message.id", constant(UUID.randomUUID().toString()))
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("activemq:queue:" + TRANSFORM_QUEUE + "?disableReplyTo=true")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("202"))
                .setBody(constant(null))
        ;

        from("activemq:queue:" + TRANSFORM_QUEUE + "?asyncConsumer=true")
                .to("file:" + incomingLogDirectory)
                .to("log:transformer:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true") // Convert from v1.4 to 2.0
                .to("log:received transformed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("activemq:queue:" + ROUTER_QUEUE)
        ;


        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        from("activemq:queue:" + ROUTER_QUEUE + "?asyncConsumer=true")
                .to("log:router:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                .when().xpath("/siri:Siri/siri:HeartbeatNotification", ns)
                    .to("activemq:queue:" + HEARTBEAT_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery", ns)
                    .to("activemq:queue:" + SITUATION_EXCHANGE_QUEUE)
                 .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery", ns)
                    .to("activemq:queue:" + VEHICLE_MONITORING_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery", ns)
                    .to("activemq:queue:" + ESTIMATED_TIMETABLE_QUEUE)
                .endChoice()
                .otherwise()
                    .to("activemq:queue:" + DEFAULT_PROCESSOR_QUEUE)
                .end()
        ;


        from("activemq:queue:" + DEFAULT_PROCESSOR_QUEUE + "?asyncConsumer=true")
                .to("log:processor:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:" + HEARTBEAT_QUEUE + "?asyncConsumer=true")
                .to("log:heartbeat:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:" + SITUATION_EXCHANGE_QUEUE + "?asyncConsumer=true")
                .to("log:processor-SX:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:" + VEHICLE_MONITORING_QUEUE + "?asyncConsumer=true")
                .to("log:processor-VM:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:" + ESTIMATED_TIMETABLE_QUEUE + "?asyncConsumer=true")
                .to("log:processor-ET:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });
    }

    private String getSubscriptionIdFromPath(String path) {
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
