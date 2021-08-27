package no.rutebanken.anshar.routes.protobuf;

import no.rutebanken.anshar.data.collections.KryoSerializer;
import org.apache.camel.builder.RouteBuilder;
import org.entur.protobuf.mapper.SiriMapper;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;
import uk.org.siri.www.siri.SiriType;

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
                })
                .bean(kryoSerializer, "write")
                .log("Compressing - done")
        ;

        from("direct:decompress.jaxb")
                .bean(kryoSerializer, "read")
                .process(p -> {
                    final String body = p.getIn().getBody(String.class);
                    p.getOut().setBody(body);
                    p.getOut().setHeaders(p.getIn().getHeaders());
                    p.getOut().setHeader(CONTENT_LENGTH, body.getBytes().length);
                })
                .log("Decompressing - done")
        ;


        from("direct:map.jaxb.to.protobuf")
                .process(p -> {
                    p.getOut().setBody(p.getIn().getBody(String.class));
                    p.getOut().setHeaders(p.getIn().getHeaders());
                })
                .bean(SiriMapper.class, "mapToPbf")
                .process(p -> {
                    final SiriType body = p.getIn().getBody(SiriType.class);
                    p.getOut().setBody(body.toByteArray());
                    p.getOut().setHeaders(p.getIn().getHeaders());
                })
        ;

        from("direct:map.protobuf.to.jaxb")
                .process(p -> {
                    p.getOut().setBody(p.getIn().getBody(byte[].class));
                    p.getOut().setHeaders(p.getIn().getHeaders());
                })
                .bean(SiriMapper.class, "mapToJaxb")
                .process(p -> {
                    final Siri body = p.getIn().getBody(Siri.class);
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

