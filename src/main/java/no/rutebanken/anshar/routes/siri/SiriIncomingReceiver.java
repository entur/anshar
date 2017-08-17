package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.CamelConfiguration;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.rutebanken.siri20.util.SiriXml;
import org.rutebanken.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

@Service
@Configuration
public class SiriIncomingReceiver extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    CamelConfiguration camelConfiguration;

    @Override
    public void configure() throws Exception {

        onException(ConnectException.class)
                .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

        errorHandler(loggingErrorHandler()
                        .log(logger)
                        .level(LoggingLevel.INFO)
        );

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        String activeMQParameters = "?disableReplyTo=true&timeToLive="+ camelConfiguration.getTimeToLive();
        String activeMqConsumerParameters = "?asyncConsumer=true&concurrentConsumers="+ camelConfiguration.getConcurrentConsumers();

        //Incoming notifications/deliveries
        from("jetty:http://0.0.0.0:" + camelConfiguration.getInboundPort() + "?matchOnUriPrefix=true&httpMethodRestrict=POST")
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                    .when(header("CamelHttpPath").contains("/services")) //Handle synchronous response
                    .process(p -> {
                        p.getOut().setHeaders(p.getIn().getHeaders());

                        String path = (String) p.getIn().getHeader("CamelHttpPath");
                        String datasetId = null;

                        String pathPattern = "/services/";
                        if (path.contains(pathPattern)) {
                                        //e.g. "/anshar/services/akt" resolves "akt"
                            datasetId = path.substring(path.indexOf(pathPattern) + pathPattern.length());
                        }
                        String query = p.getIn().getHeader("CamelHttpQuery", String.class);

                        Siri response = handler.handleIncomingSiri(null, p.getIn().getBody(InputStream.class), datasetId, handler.getIdMappingPolicy(query));
                        if (response != null) {
                            logger.info("Found ServiceRequest-response, streaming response");
                            HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);
                            SiriXml.toXml(response, null, out.getOutputStream());
                        } else {
                            p.getOut().setBody(simple(null));
                        }
                    })
                    .endChoice()
                    .when(header("CamelHttpPath").contains("/subscribe")) //Handle asynchronous response
                        .to("activemq:queue:" + CamelConfiguration.TRANSFORM_QUEUE + activeMQParameters)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                        .setBody(constant(null))
                    .endChoice()
                    .otherwise()  //Handle asynchronous response
                        .choice()
                            .when(p -> {
                                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));
                                    if (subscriptionId == null || subscriptionId.isEmpty()) {
                                        return false;
                                    }
                                    boolean existsAndIsActive = (subscriptionManager.isSubscriptionRegistered(subscriptionId) &&
                                            subscriptionManager.get(subscriptionId).isActive());

                                    if (existsAndIsActive) {
                                        subscriptionManager.touchSubscription(subscriptionId);
                                    }
                                    return existsAndIsActive;
                                })
                                    //Valid subscription
                                .to("activemq:queue:" + CamelConfiguration.TRANSFORM_QUEUE + activeMQParameters)
                                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                                .setBody(constant(null))
                            .endChoice()
                        .otherwise()
                                // Invalid subscription
                            .log("Ignoring incoming delivery for invalid subscription")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("403")) //403 Forbidden
                            .setBody(constant("Subscription is not valid"))
                        .endChoice()
                .end()
                .routeId("incoming.receive")
        ;

        //
        from("jetty:http://0.0.0.0:" + camelConfiguration.getInboundPort() + "/anshar/tmplogger")
                .to("file:" + camelConfiguration.getIncomingLogDirectory() + "/")
                .routeId("admin.filelogger")
        ;

        from("activemq:queue:" + CamelConfiguration.TRANSFORM_QUEUE + activeMqConsumerParameters)
               // .to("log:raw:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert from v1.4 to 2.0
                //.to("file:" + incomingLogDirectory + "/validator/")
                //.to("log:transformed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                    .when(exchange -> camelConfiguration.isValidationEnabled())
                        .to("activemq:queue:" + CamelConfiguration.VALIDATOR_QUEUE + activeMQParameters)
                    .endChoice()
                .end()
                .to("direct:" + CamelConfiguration.ROUTER_QUEUE)
                .routeId("incoming.transform")
        ;


        // Validate XML against schema only
        from("jetty:http://0.0.0.0:" + camelConfiguration.getInboundPort() + "/anshar/rest/sirivalidator?httpMethodRestrict=POST")
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
                .routeId("validate.receive")
        ;

        from("activemq:queue:" + CamelConfiguration.VALIDATOR_QUEUE + activeMqConsumerParameters)
                .process(p -> {
                    logger.info("XMLValidation - start");
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
                    logger.info("XMLValidation - done");

                })
                .routeId("validate.process")
        ;



        from("direct:" + CamelConfiguration.ROUTER_QUEUE)
                .to("file:" + camelConfiguration.getIncomingLogDirectory() + "?fileName=${header.CamelHttpPath}/${date:now:yyyyMMdd-hhmmss}")
                .choice()
                .when().xpath("/siri:Siri/siri:HeartbeatNotification", ns)
                    .to("activemq:queue:" + CamelConfiguration.HEARTBEAT_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:CheckStatusResponse", ns)
                    .to("activemq:queue:" + CamelConfiguration.HEARTBEAT_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery", ns)
                    .to("activemq:queue:" + CamelConfiguration.SITUATION_EXCHANGE_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery", ns)
                    .to("activemq:queue:" + CamelConfiguration.VEHICLE_MONITORING_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery", ns)
                    .to("activemq:queue:" + CamelConfiguration.ESTIMATED_TIMETABLE_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:ProductionTimetableDelivery", ns)
                    .to("activemq:queue:" + CamelConfiguration.PRODUCTION_TIMETABLE_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:DataReadyNotification", ns)
                    .to("activemq:queue:" + CamelConfiguration.FETCHED_DELIVERY_QUEUE + activeMQParameters)
                .endChoice()
                .otherwise()
                    .to("activemq:queue:" + CamelConfiguration.DEFAULT_PROCESSOR_QUEUE + activeMQParameters)
                .end()
                .routeId("incoming.redirect")
        ;


        from("activemq:queue:" + CamelConfiguration.DEFAULT_PROCESSOR_QUEUE + activeMqConsumerParameters)
                .to("log:processor:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String path = p.getIn().getHeader("CamelHttpPath", String.class);

                    String subscriptionId = getSubscriptionIdFromPath(path);
                    String datasetId = null;

                    String pathPattern = "/subscribe/";
                    if (path.contains(pathPattern)) {
                                    //e.g. "/anshar/subscribe/akt" resolves "akt"
                        datasetId = path.substring(path.indexOf(pathPattern) + pathPattern.length());
                    }

                    String query = p.getIn().getHeader("CamelHttpQuery", String.class);

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml, datasetId, handler.getIdMappingPolicy(query));

                })
                .routeId("incoming.processor.default")
        ;

        from("activemq:queue:" + CamelConfiguration.HEARTBEAT_QUEUE + activeMqConsumerParameters)
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.heartbeat")
        ;


        from("activemq:queue:" + CamelConfiguration.FETCHED_DELIVERY_QUEUE + activeMqConsumerParameters)
                .log("Processing fetched delivery")
                .process(p -> {
                    String routeName = null;

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    SubscriptionSetup subscription = subscriptionManager.get(subscriptionId);
                    if (subscription != null) {
                        routeName = subscription.getServiceRequestRouteName();
                    }

                    p.getOut().setHeader("routename", routeName);

                })
                .choice()
                .when(header("routename").isNotNull())
                    .toD("direct:${header.routename}")
                .endChoice()
                .routeId("incoming.processor.fetched_delivery")
        ;

        from("activemq:queue:" + CamelConfiguration.SITUATION_EXCHANGE_QUEUE + activeMqConsumerParameters)
                .log("Processing SX")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.sx")
        ;

        from("activemq:queue:" + CamelConfiguration.VEHICLE_MONITORING_QUEUE + activeMqConsumerParameters)
                .log("Processing VM")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.vm")
        ;

        from("activemq:queue:" + CamelConfiguration.ESTIMATED_TIMETABLE_QUEUE + activeMqConsumerParameters)
                .log("Processing ET")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.et")
        ;


        from("activemq:queue:" + CamelConfiguration.PRODUCTION_TIMETABLE_QUEUE + activeMqConsumerParameters)
                .log("Processing PT")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);

                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.pt")
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
        if (camelConfiguration.getIncomingPathPattern().startsWith("/")) {
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
        String[] parameterNames = camelConfiguration.getIncomingPathPattern().split("/");

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
