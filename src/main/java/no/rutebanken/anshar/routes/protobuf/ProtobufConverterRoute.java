package no.rutebanken.anshar.routes.protobuf;

import org.apache.camel.builder.RouteBuilder;
import org.entur.protobuf.mapper.SiriMapper;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;
import uk.org.siri.www.siri.SiriType;

@Service
public class ProtobufConverterRoute extends RouteBuilder {

    @Override
    public void configure() {

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
}

