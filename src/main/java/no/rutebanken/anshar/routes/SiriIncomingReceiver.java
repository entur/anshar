package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.routes.handlers.SiriHandler;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SiriIncomingReceiver extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String inboundPort;
    private String incomingPathPattern;
    private String incomingLogDirectory;


    public SiriIncomingReceiver(String inboundPort, String incomingPathPattern, String incomingLogDirectory) {
        this.inboundPort = inboundPort;
        this.incomingPathPattern = incomingPathPattern;
        this.incomingLogDirectory = incomingLogDirectory;
    }

    @Override
    public void configure() throws Exception {

        //Incoming notifications/deliveries
        from("netty4-http:http://0.0.0.0:" + inboundPort + "?matchOnUriPrefix=true")
                .setHeader("anshar.message.id", constant(UUID.randomUUID().toString()))
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("activemq:queue:anshar.siri.transform?disableReplyTo=true")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("202"))
                .setBody(constant(null))
        ;

        from("activemq:queue:anshar.siri.transform?asyncConsumer=true")
                .to("log:transformer:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true") // Convert from v1.4 to 2.0
                .to("file:" + incomingLogDirectory)
                .to("log:received transformed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("activemq:queue:anshar.siri.router")
        ;


        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        from("activemq:queue:anshar.siri.router?asyncConsumer=true")
                .to("log:router:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                .when().xpath("/siri:Siri/siri:HeartbeatNotification", ns)
                    .to("activemq:queue:anshar.siri.process.heartbeat")
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery", ns)
                    .to("activemq:queue:anshar.siri.process.sx")
                 .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery", ns)
                    .to("activemq:queue:anshar.siri.process.vm")
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery", ns)
                    .to("activemq:queue:anshar.siri.process.et")
                .endChoice()
                .otherwise()
                    .to("activemq:queue:anshar.siri.process")
                .end()
        ;


        from("activemq:queue:anshar.siri.process?asyncConsumer=true")
                .to("log:processor:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:anshar.siri.process.sx?asyncConsumer=true")
                .to("log:processor-SX:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:anshar.siri.process.vm?asyncConsumer=true")
                .to("log:processor-VM:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    SiriHandler.handleIncomingSiri(subscriptionId, xml);

                });

        from("activemq:queue:anshar.siri.process.et?asyncConsumer=true")
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
