package no.rutebanken.anshar.routes.messaging;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.CamelRouteNames;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.builder.xml.Namespaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

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

    @Override
    public void configure() throws Exception {

        String messageQueueCamelRoutePrefix = configuration.getMessageQueueCamelRoutePrefix();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        String activeMQParameters = (messageQueueCamelRoutePrefix.startsWith("activemq") ? "?disableReplyTo=true&timeToLive="+ configuration.getTimeToLive():"");
        String activeMqConsumerParameters = (messageQueueCamelRoutePrefix.startsWith("activemq") ? "?asyncConsumer=true&concurrentConsumers="+ configuration.getConcurrentConsumers():"");

        final String pubsubQueueName = messageQueueCamelRoutePrefix + CamelRouteNames.TRANSFORM_QUEUE;

        from("direct:enqueue.message")
                .process(p -> { // Remove lots of unecessary headers
                    p.getOut().setBody(p.getIn().getBody());
                    p.getOut().setHeader("subscriptionId", p.getIn().getHeader("subscriptionId"));
                })
//                .to("xslt:xsl/split.xsl")
//                .split().tokenizeXML("Siri").streaming()
                .to("direct:compress.jaxb")
//                .to("direct:map.jaxb.to.protobuf")
                .to(pubsubQueueName + activeMQParameters)
        ;

        from(pubsubQueueName + activeMqConsumerParameters)
                .to("direct:decompress.jaxb")
//                .to("direct:map.protobuf.to.jaxb")
                .log("Processing data from " + pubsubQueueName)
                .choice()
                    .when(header(TRANSFORM_SOAP).isEqualTo(simple(TRANSFORM_SOAP)))
                        .log("Transforming SOAP")
                        .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                    .endChoice()
                .end()
                .choice()
                    .when(header(TRANSFORM_VERSION).isEqualTo(simple(TRANSFORM_VERSION)))
                        .log("Transforming version")
                        .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert from v1.4 to 2.0
                    .endChoice()
                .end()
                .to("direct:" + CamelRouteNames.ROUTER_QUEUE)
                .routeId("incoming.transform")
        ;

        from("direct:" + CamelRouteNames.ROUTER_QUEUE)
                .choice()
                .when().xpath("/siri:Siri/siri:DataReadyNotification", ns)
                    .to("direct:"+CamelRouteNames.FETCHED_DELIVERY_QUEUE)
                .endChoice()
                .otherwise()
                    .to("direct:"+CamelRouteNames.DEFAULT_PROCESSOR_QUEUE)
                .end()
                .routeId("incoming.redirect")
        ;

        from("direct:" + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE)
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
