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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.JettyRestHttpBinding;
import org.apache.camel.http.common.HttpMessage;
import org.eclipse.jetty.server.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

public class RestRouteBuilder extends RouteBuilder {

    @Autowired
    private AnsharConfiguration configuration;

    @Override
    public void configure() throws Exception {

        restConfiguration("jetty")
                .port(configuration.getInboundPort())
                .apiContextPath("anshar/swagger.json")
                .endpointProperty("httpBindingRef", "#contentEncodingRequestFilter")
                .apiProperty("api.title", "Realtime").apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
        ;
    }
}

// To be removed according to task ROR-521
@Component
class ContentEncodingRequestFilter extends JettyRestHttpBinding {

    private static final String headerToRemove = "Content-Encoding";
    private static final String headerValueToRemove = "iso-8859-15";

    @Override
    public void readRequest(HttpServletRequest request, HttpMessage message) {
        if (((Request) request).getHttpFields().contains(headerToRemove, headerValueToRemove)) {
            ((Request) request).getHttpFields().remove(headerToRemove);
        }
        super.readRequest(request, message);
    }
}