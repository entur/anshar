/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.ProductionTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.model.rest.RestParamType;
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
public class SiriLiteRoute extends RestRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AnsharConfiguration configuration;

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
        super.configure();
        rest("/anshar/rest")
                .tag("siri.lite")

                .get("/sx").to("direct:anshar.rest.sx")
                        .param().required(false).name("datasetId").type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name("useOriginalId").type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name("maxSize").type(RestParamType.query).description("Specify max number of returned elements").dataType("int").endParam()

                .get("/vm").to("direct:anshar.rest.vm")
                        .param().required(false).name("datasetId").type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name("useOriginalId").type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name("maxSize").type(RestParamType.query).description("Specify max number of returned elements").dataType("int").endParam()

                .get("/et").to("direct:anshar.rest.et")
                        .param().required(false).name("datasetId").type(RestParamType.query).description("The id of the dataset to get").dataType("string").endParam()
                        .param().required(false).name("useOriginalId").type(RestParamType.query).description("Option to return original Ids").dataType("boolean").endParam()
                        .param().required(false).name("maxSize").type(RestParamType.query).description("Specify max number of returned elements").dataType("int").endParam()
        ;

        // Dataproviders
        from("direct:anshar.rest.sx")
                .log("RequestTracer - Incoming request (SX)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String requestorId = resolveRequestorId(request);
                    String originalId = request.getParameter("useOriginalId");
                    String maxSizeStr = request.getParameter("maxSize");

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }

                    Siri response = situations.createServiceDelivery(requestorId, datasetId, maxSize);

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(originalId));
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

        from("direct:anshar.rest.vm")
                .log("RequestTracer - Incoming request (VM)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String originalId = request.getParameter("useOriginalId");
                    String maxSizeStr = request.getParameter("maxSize");
                    String lineRef = request.getParameter("lineRef");

                    String requestorId = resolveRequestorId(request);

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }

                    Siri response;
                    if (lineRef != null) {
                        response = vehicleActivities.createServiceDelivery(lineRef);
                    } else {
                        response = vehicleActivities.createServiceDelivery(requestorId, datasetId, maxSize);
                    }


                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(originalId));
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


        from("direct:anshar.rest.et")
                .log("RequestTracer - Incoming request (ET)")
                .process(p -> {
                    p.getOut().setHeaders(p.getIn().getHeaders());

                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String datasetId = request.getParameter("agencyId");
                    if (datasetId == null) {
                        datasetId = request.getParameter("datasetId");
                    }
                    String requestorId = resolveRequestorId(request);
                    String originalId = request.getParameter("useOriginalId");
                    String maxSizeStr = request.getParameter("maxSize");
                    String lineRef = request.getParameter("lineRef");
                    String previewIntervalMinutesStr = request.getParameter("previewIntervalMinutes");

                    int maxSize = datasetId != null ? Integer.MAX_VALUE:configuration.getDefaultMaxSize();
                    if (maxSizeStr != null) {
                        try {
                            maxSize = Integer.parseInt(maxSizeStr);
                        } catch (NumberFormatException nfe) {
                            //ignore
                        }
                    }
                    long previewIntervalMillis = -1;
                    if (previewIntervalMinutesStr != null) {
                        int minutes = Integer.parseInt(previewIntervalMinutesStr);
                        previewIntervalMillis = minutes*60*1000;
                    }

                    Siri response;
                    if (lineRef != null) {
                        response = estimatedTimetables.createServiceDelivery(lineRef);
                    } else {
                        response = estimatedTimetables.createServiceDelivery(requestorId, datasetId, maxSize, previewIntervalMillis);
                    }

                    List<ValueAdapter> outboundAdapters = mappingAdapterPresets.getOutboundAdapters(SiriHandler.getIdMappingPolicy(originalId));
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

    }

    /**
     * If http-parameter requestorId is not provided in request, it will be generated based on
     * client IP and requested resource for uniqueness
     * @param request
     * @return
     */
    private String resolveRequestorId(HttpServletRequest request) {
        String requestorId = request.getParameter("requestorId");

//        if (requestorId == null) {
//            // Generating requestorId based on hash from client IP
//            String clientIpAddress = request.getHeader("X-Real-IP");
//            if (clientIpAddress == null) {
//                clientIpAddress = request.getRemoteAddr();
//            }
//            if (clientIpAddress != null) {
//                String uri = request.getRequestURI();
//                requestorId = DigestUtils.sha256Hex(clientIpAddress + uri);
//                logger.info("IP: '{}' and uri '{}' mapped to requestorId: '{}'", clientIpAddress, uri, requestorId);
//            }
//        }
        return requestorId;
    }

}
