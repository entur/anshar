package no.rutebanken.anshar.routes.export.gtfsrt;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.export.file.ExportHelper;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Date;

@Configuration
@Component
public class GtfsRtExporterRoute extends BaseRouteBuilder {

    @Value("${anshar.export.gtfsrt.cron.expression:}")
    private String snapshotCronExpression;

    @Autowired
    private ExportHelper exportHelper;

    private static final java.lang.String FILENAME = "FILENAME";
    private static final java.lang.String GTFS_RT_FILENAME = "entur_gtfs_rt.pbf";

    protected GtfsRtExporterRoute(@Autowired AnsharConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        snapshotCronExpression = "0 0/1 * ? * * *";

        if (snapshotCronExpression == null || snapshotCronExpression.isEmpty()) {
            log.info("Uploading snapshot disabled");
            return;
        }

        log.info("Creating GTFS-RT with cron-expression [{}], first creation at: {}.", snapshotCronExpression,
                new CronExpression(snapshotCronExpression).getNextValidTimeAfter(new Date()));

        singletonFrom("quartz2://anshar.export.gtfsrt?cron="+snapshotCronExpression,
                 "anshar.export.gtfsrt")
                .choice()
                .when(p -> isLeader())
                    .setHeader(FILENAME, simple(GTFS_RT_FILENAME))
                    .bean(exportHelper, "exportET")
                    .bean(exportHelper, "createGtfsRt(${body})")
                    .to("direct:anshar.export.upload.gtfsrt")
//                    .setHeader("siriDataType", simple("ET"))
////                    .to("direct:anshar.export.gtfsrt.create")
//
//                    .bean(exportHelper, "exportSX")
//                    .setHeader("siriDataType", simple("SX"))
////                    .to("direct:anshar.export.gtfsrt.create")
//
//                    .bean(exportHelper, "exportVM")
//                    .setHeader("siriDataType", simple("VM"))
////                    .to("direct:anshar.export.gtfsrt.create")
//
////                    .to("direct:anshar.export.create.gtfsrt.zip")
////                    .to("direct:anshar.export.upload.zip")
////                    .to("direct:anshar.export.delete.zip")
                .end()
        ;


        from("direct:anshar.export.upload.gtfsrt")
                .bean("blobStoreService", "uploadBlob(${header."+FILENAME+"}-${date:now:yyyyMMdd-HHmmss}.pbf, ${body})")
                .log("GTFS-RT uploaded [${header."+FILENAME+"}-${date:now:yyyyMMdd-HHmmss}.pbf]")
                .routeId("anshar.export.upload.gtfsrt");

    }

    private boolean isLeader() {
        boolean isLeader = isLeader("anshar.export.gtfsrt");
        return isLeader;
    }
}
