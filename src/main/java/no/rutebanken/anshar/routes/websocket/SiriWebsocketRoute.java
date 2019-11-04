package no.rutebanken.anshar.routes.websocket;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import java.util.List;

import static org.eclipse.jetty.http.HttpStatus.Code.BAD_REQUEST;

@Service
public class SiriWebsocketRoute extends RouteBuilder implements CamelContextAware {

    private CamelContext camelContext;

    @Value("${anshar.default.max.elements.per.websocket.delivery:100}")
    private int maximumSizePerDelivery;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    SiriObjectFactory siriObjectFactory;
    SiriHelper siriHelper;

    @Override
    public void configure() {

        List<ValueAdapter> outboundAdapters = new MappingAdapterPresets().getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT);

        siriHelper = new SiriHelper(siriObjectFactory);

        // Handling changes sent to all websocket-clients
        from("activemq:topic:anshar.outbound.estimated_timetable")
                .routeId("distribute.to.websocket.estimated_timetable")
                .bean(metrics, "countOutgoingData(${body}, WEBSOCKET)")
                .to("websocket://et?sendToAll=true")
                .log("Changes sent - ET");

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.setDefaultEndpointUri("direct:send.ws.connect.response");

        InitialServiceDeliveryProducer serviceDeliveryProducer = new InitialServiceDeliveryProducer();
        serviceDeliveryProducer.setProducer(producerTemplate);

        from("direct:send.ws.connect.response")
                .routeId("send.ws.connect.response")
                .to("log:wsConnectResponse" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .bean(metrics, "countOutgoingData(${body}, WEBSOCKET)")
                .to("websocket://et");

        // Route that handles initial data
        from("websocket://et")
                .routeId("ws.connected")
                .process( p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());
                    try {
                        Siri siri = SiriXml.parseXml(p.getIn().getBody(String.class));
                        if (siri != null && siri.getServiceRequest() != null && siri.getServiceRequest().getEstimatedTimetableRequests() != null) {

                            Siri etServiceDelivery = siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAllMonitored());

                            p.getOut().setBody(SiriValueTransformer.transform(etServiceDelivery, outboundAdapters));
                        }
                    } catch (Throwable t) {
                        p.getOut().setBody(null);
                        p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, BAD_REQUEST);
                    }

                })
                .choice()
                .when(body().isNotNull())
                    .process(serviceDeliveryProducer)
                .end()
                .log("Connected ET-client");

    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public class InitialServiceDeliveryProducer implements Processor {
        ProducerTemplate producer;

        public void setProducer(ProducerTemplate producer) {
            this.producer = producer;
        }

        public void process(Exchange inExchange) {
            Siri siri = inExchange.getIn().getBody(Siri.class);
            List<Siri> serviceDeliveries = siriHelper.splitDeliveries(siri, maximumSizePerDelivery);
            log.info("Split initial WS-delivery into {} deliveries.", serviceDeliveries.size());
            for (Siri serviceDelivery : serviceDeliveries) {
                producer.send(outExchange -> {
                    outExchange.getOut().setBody(serviceDelivery);
                    outExchange.getOut().setHeaders(inExchange.getIn().getHeaders());
                });
            }
        }
    }

}

