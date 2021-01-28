package no.rutebanken.anshar.routes.messaging;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.CamelRouteNames;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.support.builder.Namespaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_USE_ORIGINAL_ID;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_VERSION;

@Service
public class MessagingRoute extends RestRouteBuilder {

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

    @Override
    public void configure() throws Exception {

        String messageQueueCamelRoutePrefix = configuration.getMessageQueueCamelRoutePrefix();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        String queueConsumerParameters = "?concurrentConsumers="+configuration.getConcurrentConsumers();


        final String pubsubQueueSX = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_SX;
        final String pubsubQueueVM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_VM;
        final String pubsubQueueET = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_ET;
        final String pubsubQueueDefault = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_DEFAULT;

        if (messageQueueCamelRoutePrefix.contains("direct")) {
            queueConsumerParameters = "";
        }

        from("direct:enqueue.message")
                .convertBodyTo(String.class)
                .to("direct:transform.siri")
                .choice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.ESTIMATED_TIMETABLE.name()))
                        .setHeader("target_topic", simple(pubsubQueueET))
                    .endChoice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.VEHICLE_MONITORING.name()))
                        .setHeader("target_topic", simple(pubsubQueueVM))
                    .endChoice()
                    .when(header(INTERNAL_SIRI_DATA_TYPE).isEqualTo(SiriDataType.SITUATION_EXCHANGE.name()))
                        .setHeader("target_topic", simple(pubsubQueueSX))
                    .endChoice()
                    .otherwise()
                        // DataReadyNotification is processed immediately
                        .when().xpath("/siri:Siri/siri:DataReadyNotification", ns)
                            .setHeader("target_topic", simple("direct:"+CamelRouteNames.FETCHED_DELIVERY_QUEUE))
                        .endChoice()
                        .otherwise()
                            // Ensure all data is processed
                            .setHeader("target_topic", simple(pubsubQueueDefault))
                        .end()
                    .end()
                .end()
                .removeHeaders("*", "subscriptionId", "breadcrumbId", "target_topic")
                .to("direct:compress.jaxb")
                .log("Sending data to topic ${header.target_topic}")
                .toD("${header.target_topic}")
                .log("Data sent")
                .end()
        ;

        from("direct:transform.siri")
                .choice()
                    .when(header(TRANSFORM_SOAP).isEqualTo(simple(TRANSFORM_SOAP)))
                    .log("Transforming SOAP")
                    .to("xslt-saxon:xsl/siri_soap_raw.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .endChoice()
                .end()
                .choice()
                    .when(header(TRANSFORM_VERSION).isEqualTo(simple(TRANSFORM_VERSION)))
                    .log("Transforming version")
                    .to("xslt-saxon:xsl/siri_14_20.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert from v1.4 to 2.0
                .endChoice()
                .end()
        ;

        from(pubsubQueueDefault + queueConsumerParameters)
                .to("direct:decompress.jaxb")
                .log("Processing data from " + pubsubQueueDefault + ", size ${header.Content-Length}")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("incoming.transform.default")
        ;

        from(pubsubQueueSX + queueConsumerParameters)
                .to("direct:decompress.jaxb")
                .log("Processing data from " + pubsubQueueSX + ", size ${header.Content-Length}")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("incoming.transform.sx")
        ;

        from(pubsubQueueVM + queueConsumerParameters)
                .to("direct:decompress.jaxb")
                .log("Processing data from " + pubsubQueueVM + ", size ${header.Content-Length}")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("incoming.transform.vm")
        ;

        from(pubsubQueueET + queueConsumerParameters)
                .to("direct:decompress.jaxb")
                .log("Processing data from " + pubsubQueueET + ", size ${header.Content-Length}")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .routeId("incoming.transform.et")
        ;

        from("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
                .process(p -> {

                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                    String datasetId = null;

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    String useOriginalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    handler.handleIncomingSiri(subscriptionId, xml, datasetId, SiriHandler.getIdMappingPolicy(useOriginalId), -1, clientTrackingName);

                })
                .routeId("incoming.processor.default")
        ;

        from("direct:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE)
                .log("Processing fetched delivery")
                .process(p -> {
                    String routeName = null;

                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);

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

        if (configuration.getSiriVmPositionForwardingUrl() != null) {
            from("direct:forward.position.data")
                    .routeId("forward.position.data")
                    .bean(metrics, "countOutgoingData(${body}, VM_POSITION_FORWARDING)")
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .choice()
                        .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery", ns)
                            .removeHeaders("Camel*")
                            .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                            .to(configuration.getSiriVmPositionForwardingUrl())
                        .endChoice()
                    .end()
            ;
        } else {
            from("direct:forward.position.data")
                    .log(LoggingLevel.INFO, "Ignoring position-update from ${header.subscriptionId}");
        }
    }
}
