package no.rutebanken.anshar.routes.protobuf;

import org.apache.camel.builder.RouteBuilder;
import org.entur.protobuf.mapper.SiriMapper;
import org.springframework.stereotype.Service;
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

    }
}

