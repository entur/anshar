package no.rutebanken.anshar.routes.export;

import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BigDataExporter extends RouteBuilder {

    @Value("${anshar.bigdata.siri.et.export.camel.url:}")
    private String bigDataExportUrl;

    @Override
    public void configure() {

        if (bigDataExportUrl != null && bigDataExportUrl.isEmpty()) {
            bigDataExportUrl = null;
        }

        /**
         * TODO: The following code is strongly inspired/copied from Ishtar.
         *       Should consider extracting this to entur-helpers
         */

        ThreadPoolProfile bigdaddyThreadPool = new ThreadPoolProfileBuilder("big-daddy-tp-profile")
                .maxPoolSize(200)
                .maxQueueSize(2000)
                .poolSize(50)
                .rejectedPolicy(ThreadPoolRejectedPolicy.Discard)
                .build();

        getContext().getExecutorServiceManager().registerThreadPoolProfile(bigdaddyThreadPool);


        from("direct:bigdata.siri.exporter")
                .filter(f -> bigDataExportUrl != null)
                .wireTap("direct:big-daddy").executorServiceRef("big-daddy-tp-profile")
        ;

        if (bigDataExportUrl != null) {
            from("direct:big-daddy").streamCaching()
                    .to("direct:siri.transform.data")
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
//                    If data should be split up - uncomment the following
//                        .to("xslt:xsl/split.xsl")
//                        .split().tokenizeXML("Siri").streaming()
                    .doTry()
                        .setExchangePattern(ExchangePattern.OutOnly)
                        .setHeader("X-Big-Daddy-Correlation-Id", exchangeProperty(Exchange.CORRELATION_ID))
                        .to("log:push-start:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .to(bigDataExportUrl + "?bridgeEndpoint=true")
                        .to("log:push-done:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .doCatch(Exception.class)
                        .to("log:exporter:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .end();
        }
    }
}
