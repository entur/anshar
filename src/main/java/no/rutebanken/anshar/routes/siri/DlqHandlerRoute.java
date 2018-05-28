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

import no.rutebanken.anshar.routes.CamelRouteNames;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Service;

@Service
public class DlqHandlerRoute extends RouteBuilder implements CamelContextAware {

    private CamelContext camelContext;

    @Override
    public void configure() throws Exception {
        from("activemq:queue:DLQ." + CamelRouteNames.TRANSFORM_QUEUE)
                .routeId("DLQ.TRANSFORM_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE)
                .routeId("DLQ.DEFAULT_PROCESSOR_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.SITUATION_EXCHANGE_QUEUE)
                .routeId("DLQ.SITUATION_EXCHANGE_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.VEHICLE_MONITORING_QUEUE)
                .routeId("DLQ.VEHICLE_MONITORING_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.ESTIMATED_TIMETABLE_QUEUE)
                .routeId("DLQ.ESTIMATED_TIMETABLE_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.PRODUCTION_TIMETABLE_QUEUE)
                .routeId("DLQ.PRODUCTION_TIMETABLE_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.HEARTBEAT_QUEUE)
                .routeId("DLQ.HEARTBEAT_QUEUE")
                .to("direct:ignore");
        from("activemq:queue:DLQ." + CamelRouteNames.FETCHED_DELIVERY_QUEUE)
                .routeId("DLQ.FETCHED_DELIVERY_QUEUE")
                .to("direct:ignore");

        from("direct:ignore")
                .routeId("Ignore.DLQ")
                .log("Ignoring DLQ");
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
