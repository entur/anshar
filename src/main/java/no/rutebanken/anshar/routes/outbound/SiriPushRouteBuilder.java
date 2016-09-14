package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;

import java.util.UUID;

public class SiriPushRouteBuilder extends RouteBuilder {

    private final boolean soapRequest;
    private String remoteEndPoint;
    private RouteDefinition definition;
    private String routeName;

    public SiriPushRouteBuilder(String remoteEndPoint, boolean soapRequest) {
        this.remoteEndPoint=remoteEndPoint;
        this.soapRequest = soapRequest;
    }

    @Override
    public void configure() throws Exception {

        if (remoteEndPoint.startsWith("http://")) {
            remoteEndPoint = remoteEndPoint.substring("http://".length());
        }

        routeName = String.format("direct:%s", UUID.randomUUID().toString());

        if (soapRequest) {
            definition = from(routeName)
                    .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                    .setHeader("CamelHttpMethod", constant("POST"))
                    .to("http4://" + remoteEndPoint);
        } else {
            definition = from(routeName)
                    .setHeader("CamelHttpMethod", constant("POST"))
                    .to("http4://" + remoteEndPoint);
        }

    }

    public RouteDefinition getDefinition() {
        return definition;
    }

    public String getRouteName() {
        return routeName;
    }
}