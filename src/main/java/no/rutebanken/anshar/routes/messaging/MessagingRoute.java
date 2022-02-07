package no.rutebanken.anshar.routes.messaging;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.CamelRouteNames;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.admin.AdminRouteHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Predicate;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

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

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    private AdminRouteHelper adminRouteHelper;

    @Override
    public void configure() throws Exception {

        String messageQueueCamelRoutePrefix = configuration.getMessageQueueCamelRoutePrefix();

        String queueConsumerParameters = "?concurrentConsumers="+configuration.getConcurrentConsumers();


        final String pubsubQueueSX = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_SX;
        final String pubsubQueueVM = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_VM;
        final String pubsubQueueET = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_ET;
        final String pubsubQueueDefault = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE_DEFAULT;

        if (messageQueueCamelRoutePrefix.contains("direct")) {
            queueConsumerParameters = "";
        }

        from("direct:process.message.synchronous")
                .convertBodyTo(String.class)
                .to("direct:transform.siri")
                .to("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
        ;

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
                            .to("log:not_processed:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .end()
                    .end()
                .end()
                .removeHeaders("*", "subscriptionId", "breadcrumbId", "target_topic")
                .to("direct:compress.jaxb")
                .log("Sending data to topic ${header.target_topic}")
                .setHeader(GooglePubsubConstants.ORDERING_KEY, () -> System.currentTimeMillis())
                .toD("${header.target_topic}")
                .bean(subscriptionManager, "dataReceived(${header.subscriptionId})")
                .end()
                .routeId("add.to.queue")
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
                .to("direct:process.mapping")
                .to("direct:format.xml")
        ;


        from("direct:process.mapping")
                .process(p -> {
                    SubscriptionSetup subscriptionSetup = subscriptionManager.get(p.getIn().getHeader("subscriptionId", String.class));
                    Siri originalInput = siriXmlValidator.parseXml(subscriptionSetup, p.getIn().getBody(String.class));

                    Siri incoming = SiriValueTransformer.transform(originalInput, subscriptionSetup.getMappingAdapters(), false, true);

                    p.getMessage().setHeaders(p.getIn().getHeaders());
                    p.getMessage().setBody(SiriXml.toXml(incoming));
                })
        ;

        from("direct:format.xml")
            .to("xslt-saxon:xsl/indent.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory")
            .routeId("incoming.format.xml")
        ;

        // When shutdown has been triggered - stop processing data from pubsub
        Predicate readFromPubsub = exchange -> adminRouteHelper.isNotShuttingDown();
//        if (configuration.processData()) {
//            from(pubsubQueueDefault + queueConsumerParameters)
//                    .choice().when(readFromPubsub)
//                    .log("Processing data from " + pubsubQueueDefault + ", size ${header.Content-Length}")
//                    .to("direct:decompress.jaxb")
//                    .to("direct:process.queue.default.async")
//                    .endChoice()
//                    .startupOrder(100004)
//                    .routeId("incoming.transform.default")
//            ;
//        }
        if (configuration.processSX()) {
            from(pubsubQueueSX + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .log("Processing data from " + pubsubQueueSX + ", size ${header.Content-Length}")
                    .to("direct:decompress.jaxb")
                    .to("direct:process.queue.default.async")
                    .endChoice()
                    .startupOrder(100003)
                    .routeId("incoming.transform.sx")
            ;
        }

        if (configuration.processVM()) {
            from(pubsubQueueVM + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .log("Processing data from " + pubsubQueueVM + ", size ${header.Content-Length}")
                    .to("direct:decompress.jaxb")
                    .to("direct:process.queue.default.async")
                    .endChoice()
                    .startupOrder(100002)
                    .routeId("incoming.transform.vm")
            ;
        }

        if (configuration.processET()) {
            from(pubsubQueueET + queueConsumerParameters)
                    .choice().when(readFromPubsub)
                    .log("Processing data from " + pubsubQueueET + ", size ${header.Content-Length}")
                    .to("direct:decompress.jaxb")
                    .to("direct:process.queue.default.async")
                    .endChoice()
                    .startupOrder(100001)
                    .routeId("incoming.transform.et")
            ;
        }

        from("direct:process.queue.default.async")
            .wireTap("direct:" + CamelRouteNames.PROCESSOR_QUEUE_DEFAULT)
            .routeId("process.queue.default.async")
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
    }
}
