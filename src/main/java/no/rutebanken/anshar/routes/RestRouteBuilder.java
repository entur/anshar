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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.bind.UnmarshalException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

public class RestRouteBuilder extends RouteBuilder {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AnsharConfiguration configuration;

    @Override
    public void configure() throws Exception {

        restConfiguration("jetty")
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

        errorHandler(loggingErrorHandler()
                .log(logger)
                .level(LoggingLevel.INFO)
        );

        from("direct:anshar.invalid.tracking.header.response")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400")) //400 Bad request
                .setBody(constant("Missing required header (" + configuration.getTrackingHeaderName() + ")"))
                .routeId("reject.request.missing.header")
        ;

    }
    protected boolean isTrackingHeaderAcceptable(Exchange e) {
        String camelHttpMethod = (String) e.getIn().getHeader("CamelHttpMethod");
        if (isTrackingRequired(camelHttpMethod)) {
            String header = (String) e.getIn().getHeader(configuration.getTrackingHeaderName());
            return header != null && !header.isEmpty();
        }
        return true;
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