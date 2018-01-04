package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.CamelConfiguration;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.MappingAdapterPresets;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpHeaders;
import org.rutebanken.siri20.util.SiriJson;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Service
public class SiriProvider extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private CamelConfiguration configuration;

    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private ProductionTimetables productionTimetables;
    
    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    @Override
    public void configure() throws Exception {


        // Dataproviders
        from("jetty:http://0.0.0.0:" + configuration.getInboundPort() + "/anshar/rest/sx?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (SX)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");
                    String maxSizeStr = request.getParameter("maxSize");

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:1000;
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }

                    Siri response = situations.createServiceDelivery(requestorId, datasetId, maxSize);

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(request.getQueryString()));
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                            "application/json".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml");
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (SX)")
                .routeId("incoming.rest.sx")
        ;

        from("jetty:http://0.0.0.0:" + configuration.getInboundPort() + "/anshar/rest/vm?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (VM)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");
                    String maxSizeStr = request.getParameter("maxSize");

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:1000;
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }

                    Siri response = vehicleActivities.createServiceDelivery(requestorId, datasetId, maxSize);

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(request.getQueryString()));
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                            "application/json".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml");
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (VM)")
                .routeId("incoming.rest.vm")
        ;


        from("jetty:http://0.0.0.0:" + configuration.getInboundPort() + "/anshar/rest/et?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (ET)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");
                    String maxSizeStr = request.getParameter("maxSize");
                    String lineRef = request.getParameter("lineRef");

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:1000;
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }

                    Siri response;
                    if (lineRef != null) {
                        response = estimatedTimetables.createServiceDelivery(lineRef);
                    } else {
                        response = estimatedTimetables.createServiceDelivery(requestorId, datasetId, maxSize);
                    }

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(request.getQueryString()));
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                            "application/json".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml");
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (ET)")
                .routeId("incoming.rest.et")
        ;


        from("jetty:http://0.0.0.0:" + configuration.getInboundPort() + "/anshar/rest/pt?httpMethodRestrict=GET")
                .log("RequestTracer - Incoming request (PT)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String requestorId = request.getParameter("requestorId");
                    String originalId = request.getParameter("useOriginalId");

                    Siri response = siriObjectFactory.createPTServiceDelivery(productionTimetables.getAllUpdates(requestorId, datasetId));

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(request.getQueryString()));
                    if ("test".equals(originalId)) {
                        outboundAdapters = null;
                    }
                    response = SiriValueTransformer.transform(response, outboundAdapters);

                    HttpServletResponse out = p.getIn().getBody(HttpServletResponse.class);

                    if ("application/json".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
                            "application/json".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                        SiriJson.toJson(response, out.getOutputStream());
                    } else {
                        out.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml");
                        SiriXml.toXml(response, null, out.getOutputStream());
                    }
                })
                .log("RequestTracer - Request done (PT)")
                .routeId("incoming.rest.pt")
        ;

    }

}
