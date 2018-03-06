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

    @Value("${anshar.export.gtfsrt.tmpFolder:/tmp/anshar/gtfsrt}")
    private String tmpFolder;

    @Value("${anshar.export.gtfsrt.cron.expression}")
    private String snapshotCronExpression;

    @Autowired
    private ExportHelper exportHelper;

    final static String TMP_FOLDER = "AnsharTmpFolder";


    protected GtfsRtExporterRoute(@Autowired AnsharConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (snapshotCronExpression == null || snapshotCronExpression.isEmpty()) {
            log.info("Uploading snapshot disabled");
            return;
        }

        if (tmpFolder.endsWith("/")) {
            tmpFolder = tmpFolder.substring(0, tmpFolder.length()-1);
        }

        log.info("Creating GTFS-RT with cron-expression [{}], first creation at: {}.", snapshotCronExpression,
                new CronExpression(snapshotCronExpression).getNextValidTimeAfter(new Date()));

        singletonFrom("quartz2://anshar.export.gtfsrt?cron="+snapshotCronExpression,
                 "anshar.export.gtfsrt")
                .choice()
                .when(p -> isLeader())
                    .setHeader(TMP_FOLDER, simple(tmpFolder))
                    .bean(exportHelper, "exportET")
                    .bean(exportHelper, "createGtfsRt(${body})")
                    .setHeader("siriDataType", simple("ET"))
//                    .to("direct:anshar.export.gtfsrt.create")

                    .bean(exportHelper, "exportSX")
                    .setHeader("siriDataType", simple("SX"))
//                    .to("direct:anshar.export.gtfsrt.create")

                    .bean(exportHelper, "exportVM")
                    .setHeader("siriDataType", simple("VM"))
//                    .to("direct:anshar.export.gtfsrt.create")

//                    .to("direct:anshar.export.create.zip")
//                    .to("direct:anshar.export.upload.zip")
//                    .to("direct:anshar.export.delete.zip")
//                .end()

        ;

    }

    private boolean isLeader() {
        boolean isLeader = isLeader("anshar.export.gtfsrt");
        log.info("Is leader: {}", isLeader);
        return isLeader;
    }
}
