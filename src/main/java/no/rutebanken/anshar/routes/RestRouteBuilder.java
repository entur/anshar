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

package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.config.AnsharConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.builder.Namespaces;
import org.apache.http.HttpHeaders;
import org.entur.protobuf.mapper.SiriMapper;
import org.rutebanken.siri20.util.SiriJson;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.org.siri.siri20.Siri;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

public class RestRouteBuilder extends RouteBuilder {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
            .add("xsd", "http://www.w3.org/2001/XMLSchema");


    @Value("${anshar.data.handler.baseurl.vm:}")
    protected String vmHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.et:}")
    protected String etHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.sx:}")
    protected String sxHandlerBaseUrl;

    @Autowired
    private AnsharConfiguration configuration;

    private static boolean isDataHandlersInitialized = false;

    @Override
    public void configure() throws Exception {

        restConfiguration()
                .component("jetty")
                .port(configuration.getInboundPort())
                .apiContextPath("anshar/swagger.json")
//                .endpointProperty("httpBindingRef", "#contentEncodingRequestFilter")
                .apiProperty("api.title", "Realtime").apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
        ;

        onException(ConnectException.class)
                .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

        onException(UnmarshalException.class, InvalidPayloadException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400"))
                .setBody(simple("Invalid XML"))
        ;

        errorHandler(defaultErrorHandler()
                .log(logger)
                .loggingLevel(LoggingLevel.INFO)
        );

        if (!isDataHandlersInitialized) {
            isDataHandlersInitialized=true;
            createClientRequestRoutes();
        }

        from("direct:anshar.invalid.tracking.header.response")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant("Missing required header (" + configuration.getTrackingHeaderName() + ")"))
                .routeId("reject.request.missing.header")
        ;

        from("direct:anshar.blocked.tracking.header.response")
            .removeHeaders("*")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
            .setBody(constant(""))
            .routeId("reject.request.blocked.header")
        ;

    }

    /*
     * Creates routes to handle routing of incoming requests based on the mode the instance is started with
     *
     * PROXY redirects requests to et/vm/sx-instances
     */
    protected void createClientRequestRoutes() {

        if (configuration.processET()) {
            from("direct:process.et.subscription.request")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.et.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.et.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.et")
                    .to("direct:internal.anshar.rest.et")
            ;
            from("direct:anshar.rest.et.cached")
                    .to("direct:internal.anshar.rest.et.cached")
            ;
            from("direct:anshar.rest.et.monitored")
                    .to("direct:internal.anshar.rest.et.monitored")
            ;
            from("direct:anshar.rest.et.monitored.cached")
                    .to("direct:internal.anshar.rest.et.monitored.cached")
            ;
        } else {
            from("direct:process.et.subscription.request")
                    .to("direct:redirect.request.et")
            ;
            from("direct:process.et.service.request")
                    .to("direct:redirect.request.et")
            ;
            from("direct:process.et.service.request.cache")
                    .to("direct:redirect.request.et")
            ;
            //REST
            from("direct:anshar.rest.et")
                    .to("direct:redirect.request.et")
            ;
            from("direct:anshar.rest.et.cached")
                    .to("direct:redirect.request.et")
            ;
            from("direct:anshar.rest.et.monitored")
                    .to("direct:internal.anshar.rest.et.monitored")
            ;
            from("direct:anshar.rest.et.monitored.cached")
                    .to("direct:redirect.request.et")
            ;
            from("direct:redirect.request.et")
                    .to(etHandlerBaseUrl + "${header.CamelHttpUri}?bridgeEndpoint=true")
            ;
        }

        if (configuration.processVM()) {
            from("direct:process.vm.subscription.request")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.vm.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.vm.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;
            //REST
            from("direct:anshar.rest.vm")
                    .to("direct:internal.anshar.rest.vm")
            ;
            from("direct:anshar.rest.vm.cached")
                    .to("direct:internal.anshar.rest.vm.cached")
            ;

        } else {
            from("direct:process.vm.subscription.request")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:process.vm.service.request")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:process.vm.service.request.cache")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:anshar.rest.vm")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:anshar.rest.vm.cached")
                    .to("direct:redirect.request.vm")
            ;
            from("direct:redirect.request.vm")
                    .to(vmHandlerBaseUrl + "${header.CamelHttpUri}?bridgeEndpoint=true")
            ;
        }

        if (configuration.processSX()) {
            from("direct:process.sx.subscription.request")
                    .to("direct:internal.handle.subscription")
            ;
            from("direct:process.sx.service.request")
                    .to("direct:internal.process.service.request")
            ;
            from("direct:process.sx.service.request.cache")
                    .to("direct:internal.process.service.request.cache")
            ;

            //REST
            from("direct:anshar.rest.sx")
                    .to("direct:internal.anshar.rest.sx")
            ;
            from("direct:anshar.rest.sx.cached")
                    .to("direct:internal.anshar.rest.sx.cached")
            ;
        } else {
            from("direct:process.sx.subscription.request")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:process.sx.service.request")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:process.sx.service.request.cache")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:anshar.rest.sx")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:anshar.rest.sx.cached")
                    .to("direct:redirect.request.sx")
            ;
            from("direct:redirect.request.sx")
                    .to(sxHandlerBaseUrl + "${header.CamelHttpUri}?bridgeEndpoint=true")
            ;
        }
    }

    protected boolean isTrackingHeaderAcceptable(Exchange e) {
        String camelHttpMethod = (String) e.getIn().getHeader("CamelHttpMethod");

        String header = e.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);
        if (header != null && configuration.getBlockedEtClientNames().contains(header)) {
            logger.info("Blocked request from {} = {}", configuration.getTrackingHeaderName(), header);
            return false;
        }
        if (isTrackingRequired(camelHttpMethod)) {
            return header != null && !header.isEmpty();
        }
        return true;
    }

    /**
     * Returns true if Et-Client-header is blocked - request should be ignored
     * @param e
     * @return
     */
    protected boolean isTrackingHeaderBlocked(Exchange e) {

        String header = (String) e.getIn().getHeader(configuration.getTrackingHeaderName());
        if (header != null && configuration.getBlockedEtClientNames().contains(header)) {
            logger.info("Blocked request from {} = {}", configuration.getTrackingHeaderName(), header);
            return true;
        }
        return false;
    }

    private boolean isTrackingRequired(String httpMethod) {
        if ("POST".equals(httpMethod) && configuration.isTrackingHeaderRequiredforPost()) {
            return true;
        }
        if ("GET".equals(httpMethod) && configuration.isTrackingHeaderRequiredForGet()) {
            return true;
        }
        return false;
    }

    protected String getSubscriptionIdFromPath(String path) {
        if (configuration.getIncomingPathPattern().startsWith("/")) {
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
        String[] parameterNames = configuration.getIncomingPathPattern().split("/");

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
    protected void streamOutput(Exchange p, Siri response, HttpServletResponse out) throws IOException, JAXBException {

        if (MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
            MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
            out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            SiriJson.toJson(response, out.getOutputStream());
        } else if ("application/x-protobuf".equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE)) |
            "application/x-protobuf".equals(p.getIn().getHeader(HttpHeaders.ACCEPT))) {
            try {
                final byte[] bytes = SiriMapper.mapToPbf(response).toByteArray();
                out.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-protobuf");
                out.setHeader(HttpHeaders.CONTENT_LENGTH, "" + bytes.length);
                out.getOutputStream().write(bytes);
            } catch (NullPointerException npe) {
                File file = new File("ET-" + System.currentTimeMillis() + ".xml");
                log.error("Caught NullPointerException, data written to " + file.getAbsolutePath(), npe);
                SiriXml.toXml(response, null, new FileOutputStream(file));
            }
        } else {
            out.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
            SiriXml.toXml(response, null, out.getOutputStream());
        }
        p.getMessage().setBody(out.getOutputStream());
    }

}

// To be removed according to task ROR-521
//@Component
//class ContentEncodingRequestFilter extends JettyRestHttpBinding {
//
//    private static final String headerToRemove = "Content-Encoding";
//    private static final String headerValueToRemove = "iso-8859-15";
//
//    @Override
//    public void readRequest(HttpServletRequest request, HttpMessage message) {
//        if (((Request) request).getHttpFields().contains(headerToRemove, headerValueToRemove)) {
//            ((Request) request).getHttpFields().remove(headerToRemove);
//        }
//        super.readRequest(request, message);
//    }
//}