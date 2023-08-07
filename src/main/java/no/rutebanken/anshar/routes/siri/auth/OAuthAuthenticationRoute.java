package no.rutebanken.anshar.routes.siri.auth;

import jakarta.ws.rs.core.MediaType;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class OAuthAuthenticationRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        from("direct:oauth2.authorize")
                .choice()
                .when(header("oauth-server").isNotNull())
                    .setHeader("CamelHttpMethod").simple("POST")
                    .choice()
                    .when(header("oauth-contentType").isEqualTo(simple(MediaType.APPLICATION_FORM_URLENCODED)))
                        .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_FORM_URLENCODED))
                        .setBody(
                                simple("grant_type=${header.oauth-grant-type}&client_id=${header.oauth-client-id}&client_secret=${header.oauth-client-secret}&scope=${header.oauth-scope}&audience=${header.oauth-audience}")
                        )
                    .otherwise()
                        .setHeader(Exchange.CONTENT_TYPE).simple(MediaType.APPLICATION_JSON)
                        .setBody(
                            simple("{\"grant_type\": \"${header.oauth-grant-type}\", \"client_id\": \"${header.oauth-client-id}\", \"client_secret\": \"${header.oauth-client-secret}\", \"audience\": \"${header.oauth-audience}\"}")
                        )
                    .endChoice()
                    .toD("${header.oauth-server}")
                    .unmarshal().json(JsonLibrary.Jackson, ResponseToken.class)
                    .choice()
                    .when().simple("${header.CamelHttpResponseCode} == 200")
                        .setHeader("Authorization").simple("${body.token_type} ${body.access_token}")
                        .log("Authenticated!!!")
                    .otherwise()
                        .log("Not Authenticated!!!")
                    .endChoice()
                .otherwise()
                    .log("No OAuth configured - skipping")
                .end()
                .removeHeaders("oauth*") //Always clean up to avoid secrets being exposed
                .routeId("anshar.oauth2.authorize")
        ;
    }
}
