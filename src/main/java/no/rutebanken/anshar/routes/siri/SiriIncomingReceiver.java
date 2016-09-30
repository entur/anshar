package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.rutebanken.siri20.util.SiriXml;
import org.rutebanken.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

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

    @Value("${anshar.validation.enabled}")
    private boolean validationEnabled = false;

    private SiriHandler handler;

    public SiriIncomingReceiver() {
        handler = new SiriHandler();
    }

    @Override
    public void configure() throws Exception {

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        //Incoming notifications/deliveries
        from("jetty:http://0.0.0.0:" + inboundPort + "?matchOnUriPrefix=true&httpMethodRestrict=POST")
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                    .when(header("CamelHttpPath").endsWith("/services")) //Handle synchronous response
                    .process(p -> {
                        Siri response = handler.handleIncomingSiri(null, p.getIn().getBody(String.class));
                        if (response != null) {
                            p.getOut().setBody(SiriXml.toXml(response));
                        } else {
                            p.getOut().setBody(simple(null));
                        }
                    })
                .endChoice()
                .otherwise()  //Handle asynchronous response
                    .to("activemq:queue:" + TRANSFORM_QUEUE + "?disableReplyTo=true")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                    .setBody(constant(null))
                .end()
        ;

        from("activemq:queue:" + TRANSFORM_QUEUE + "?asyncConsumer=true")
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .to("log:transformed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                    .when(exchange -> validationEnabled)
                        .to("file:" + incomingLogDirectory + "/validator/")
                        .process(p -> {
                            SiriValidator.Version siriVersion = SiriValidator.Version.VERSION_2_0;

                            File targetFile = new File(p.getIn().getHeader("CamelFileNameProduced") + "_report");

                            File parent = targetFile.getParentFile();
                            if (!parent.exists() && !parent.mkdirs()) {
                                throw new IllegalStateException("Couldn't create dir: " + parent);
                            }

                            FileOutputStream fos = new FileOutputStream(targetFile);
                            PrintStream ps = new PrintStream(fos);

                            ps.println(p.getIn().getHeader("CamelHttpPath", String.class));
                            ps.println("Validating XML as " + siriVersion);

                            String xml = p.getIn().getBody(String.class);
                            SiriValidator.validate(xml, siriVersion, ps);

                            fos.close();

                        })
                    .endChoice()
                .end()
                .to("activemq:queue:" + ROUTER_QUEUE)
        ;


        // Validate XML against schema only
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/rest/sirivalidator?httpMethodRestrict=POST")
                .process(p -> {
                    String xml = p.getIn().getBody(String.class);
                    if (xml != null) {
                        logger.info("XML-validator started");

                        HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                        String version = request.getParameter("version");

                        SiriValidator.Version siriVersion = resolveSiriVersionFromString(version);

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        PrintStream ps = new PrintStream(outputStream);
                        ps.println("Validating XML as " + siriVersion);

                        boolean validXml = SiriValidator.validate(xml, siriVersion, ps);

                        logger.info("XML-validator - valid: " + validXml);

                        p.getOut().setBody(outputStream.toString("UTF-8"));
                        ps.close();
                    }
                })
        ;

        from("activemq:queue:" + ROUTER_QUEUE + "?asyncConsumer=true")
                .choice()
                .when().xpath("/siri:Siri/siri:HeartbeatNotification", ns)
                    .to("activemq:queue:" + HEARTBEAT_QUEUE)
                .endChoice()
                .when().xpath("/siri:Siri/siri:CheckStatusResponse", ns)
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
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:ProductionTimetableDelivery", ns)
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
                .to("log:processor-sx:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_sx?sendToAll=true")
        ;

        from("activemq:queue:" + VEHICLE_MONITORING_QUEUE + "?asyncConsumer=true")
                .to("log:processor-vm:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_vm?sendToAll=true")
        ;

        from("activemq:queue:" + ESTIMATED_TIMETABLE_QUEUE + "?asyncConsumer=true")
                .to("log:processor-et:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);

                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_et?sendToAll=true")
        ;


        from("activemq:queue:" + PRODUCTION_TIMETABLE_QUEUE + "?asyncConsumer=true")
                .to("log:processor-pt:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    String xml = p.getIn().getBody(String.class);

                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                //.to("websocket://siri_pt?sendToAll=true")
        ;

    }

    private SiriValidator.Version resolveSiriVersionFromString(String version) {
        if (version != null) {
            switch (version) {
                case "1.0":
                    return SiriValidator.Version.VERSION_1_0;
                case "1.3":
                    return SiriValidator.Version.VERSION_1_3;
                case "1.4":
                    return SiriValidator.Version.VERSION_1_4;
                case "2.0":
                    return SiriValidator.Version.VERSION_2_0;
            }
        }
        return SiriValidator.Version.VERSION_2_0;
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


        Map<String, String> values = calculatePathVariableMap(path);
        logger.trace("Incoming delivery {}", values);

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
