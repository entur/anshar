package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MappingDatasetUpdaterRoute extends BaseRouteBuilder {


    @Value("${anshar.mapping.jbvCode.gcs.path}")
    private String jbvCodeStopPlaceMappingPath;
    @Value("${anshar.mapping.jbvCode.url}")
    private String jbvCodeStopPlaceMappingUrl;

    @Value("${anshar.mapping.quays.gcs.path}")
    private String quayMappingPath;
    @Value("${anshar.mapping.quays.url}")
    private String quayMappingUrl;

    @Value("${anshar.mapping.stopplaces.gcs.path}")
    private String stopPlaceMappingPath;
    @Value("${anshar.mapping.stopplaces.url}")
    private String stopPlaceMappingUrl;

    @Value("${anshar.mapping.update.frequency.min}")
    private int frequencyMinutes;

    @Autowired
    BlobStoreService blobStoreService;

    protected MappingDatasetUpdaterRoute(@Autowired AnsharConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() {

        if (quayMappingUrl != null && !quayMappingUrl.isEmpty() &&
                quayMappingPath != null && !quayMappingPath.isEmpty()) {
            singletonFrom("quartz2://anshar.import.mapping.quay?cron=0 0/60 0/1 ? * * *"
                    , "anshar.import.mapping.quay")
                    .choice()
                    .when(p -> isLeader("anshar.import.mapping.quay"))

                    .setHeader("url", simple(quayMappingUrl))
                    .setHeader("name", simple(quayMappingPath))
                    .to("direct:process.mapping.update")

                    .end()
            ;
        }
        if (stopPlaceMappingUrl != null && !stopPlaceMappingUrl.isEmpty() &&
                stopPlaceMappingPath != null && !stopPlaceMappingPath.isEmpty()) {
            singletonFrom("quartz2://anshar.import.mapping.stop_place?cron=0 5/60 0/1 ? * * *"
                    , "anshar.import.mapping.stop_place")
                    .choice()
                    .when(p -> isLeader("anshar.import.mapping.stop_place"))
                    .setHeader("url", simple(stopPlaceMappingUrl))
                    .setHeader("name", simple(stopPlaceMappingPath))
                    .to("direct:process.mapping.update")
                    .end()
            ;
        }
        if (jbvCodeStopPlaceMappingUrl != null && !jbvCodeStopPlaceMappingUrl.isEmpty() &&
                jbvCodeStopPlaceMappingPath != null && !jbvCodeStopPlaceMappingPath.isEmpty()) {
            singletonFrom("quartz2://anshar.import.mapping.jbv_code?cron=0 10/60 0/1 ? * * *"
                    , "anshar.import.mapping.jbv_code")
                    .choice()
                    .when(p -> isLeader("anshar.import.mapping.jbv_code"))

                    .setHeader("url", simple(jbvCodeStopPlaceMappingUrl))
                    .setHeader("name", simple(jbvCodeStopPlaceMappingPath))
                    .to("direct:process.mapping.update")

                    .end()
            ;
        }

        from("direct:process.mapping.update")
                .setHeader("ET-Client-Name", constant("anshar-mapping"))
                .toD("${header.url}")
                .log("Updating ${header.name} from ${header.url}")
                .bean(blobStoreService, "uploadBlob(${header.name}, ${body}, false)")
                .removeHeaders("CamelHttp*")
        ;

//        from("direct:read.data.from.url")
//                .toD("${header.url}")
////                .setBody(body().convertToString())
//        ;
    }
}
