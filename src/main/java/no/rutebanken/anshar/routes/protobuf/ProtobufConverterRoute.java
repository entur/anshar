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
package no.rutebanken.anshar.routes.protobuf;

import no.rutebanken.anshar.data.collections.KryoSerializer;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import static org.apache.camel.Exchange.CONTENT_LENGTH;

@Service
public class ProtobufConverterRoute extends RouteBuilder {

    KryoSerializer kryoSerializer = new KryoSerializer();

    @Override
    public void configure() {


        from("direct:compress.jaxb")
                .setBody(body().convertToString())
                .process(p -> {
                    final String body = fixEncodingErrorsInXml(p.getIn().getBody(String.class), p.getIn().getHeader("subscriptionId", String.class));
                    p.getOut().setBody(body);
                    p.getOut().setHeaders(p.getIn().getHeaders());
                    p.getOut().setHeader(CONTENT_LENGTH, body.getBytes(StandardCharsets.UTF_8).length);
                })
                .bean(kryoSerializer, "write")
        ;

        from("direct:decompress.jaxb")
                .bean(kryoSerializer, "read")
                .process(p -> {
                    final String body = p.getIn().getBody(String.class);
                    p.getOut().setBody(body);
                    p.getOut().setHeaders(p.getIn().getHeaders());
                })
        ;

    }

    /*
     * Temporarily replaces characters when receiving data created by wrong encoding - KOLDATA-479
     */
    private String fixEncodingErrorsInXml(String body, String subscriptionId) {

        if (body == null) {
            // This should never happen (!), keeping it for now, but should be removed
            body = "";
            log.warn("Body is null!!!");
        }

        boolean replacedChars = false;
        if (body.indexOf("Ã¦") >= 0) {
            body = body.replaceAll("Ã¦", "æ");
            replacedChars = true;
        }
        if (body.indexOf("Ã†") >= 0) {
            body = body.replaceAll("Ã†", "Æ");
            replacedChars = true;
        }
        if (body.indexOf("Ã¸") >= 0) {
            body = body.replaceAll("Ã¸", "ø");
            replacedChars = true;
        }
        if (body.indexOf("Ã\u0098") >= 0) {
            body = body.replaceAll("Ã\u0098", "Ø");
            replacedChars = true;
        }
        if (body.indexOf("Ã¥") >= 0) {
            body = body.replaceAll("Ã¥", "å");
            replacedChars = true;
        }
        if (body.indexOf("Ã\u0085") >= 0) {
            body = body.replaceAll("Ã\u0085", "Å");
            replacedChars = true;
        }

        if (replacedChars) {
            log.info("Fixed encoding errors for subscriptionId: {}", subscriptionId);
        }

        return body;
    }
}

