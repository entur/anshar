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

package no.rutebanken.anshar.routes.siri.transformer;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.util.List;

@Service
public class SiriOutputTransformerRoute extends RouteBuilder {

    public static final String OUTPUT_ADAPTERS_HEADER_NAME = "adapters";

    @Override
    public void configure() {
        final List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT);

        from("direct:siri.transform.data")
                .process(p -> {
                    List<ValueAdapter> adapters;

                    if (p.getIn().getHeader(OUTPUT_ADAPTERS_HEADER_NAME) != null) {
                        adapters = (List<ValueAdapter>) p.getIn().getHeader(OUTPUT_ADAPTERS_HEADER_NAME);
                        p.getIn().removeHeader(OUTPUT_ADAPTERS_HEADER_NAME);
                    } else {
                        adapters = outboundAdapters;
                    }

                    p.getOut().setBody(SiriValueTransformer.transform(
                        p.getIn().getBody(Siri.class),
                        adapters,
                        false,
                        false));
                    p.getOut().setHeaders(p.getIn().getHeaders());
                })
                .log(LoggingLevel.DEBUG, "Transformed SIRI")
                .routeId("siri.transformer.route")
            ;
    }

}
