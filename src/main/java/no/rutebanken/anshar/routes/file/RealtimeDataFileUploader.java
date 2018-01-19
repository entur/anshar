package no.rutebanken.anshar.routes.file;

import no.rutebanken.anshar.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.CamelConfiguration;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.apache.camel.Exchange.FILE_NAME;

@Configuration
@Component
public class RealtimeDataFileUploader extends BaseRouteBuilder {

    @Value("${anshar.export.snapshot.tmpFolder:/tmp/anshar/export}")
    private String tmpFolder;

    @Value("${anshar.export.snapshot.interval.minutes:-1}")
    private int snapshotInterval;

    @Autowired
    private ExportHelper exportHelper;
    private String TMP_FOLDER = "AnsharTmpFolder";

    protected RealtimeDataFileUploader(@Autowired CamelConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (snapshotInterval > 0) {
            log.info("Uploading snapshot every {} minutes", snapshotInterval);
            singletonFrom("quartz2://anshar.file.et.uploader?fireNow=true&trigger.repeatInterval=" + (snapshotInterval * 60 * 1000)
                    ,"anshar.export.snapshot")
                    .setHeader(TMP_FOLDER, simple("${date:now:yyMMddHHmmssSSS}"))
                    .bean(exportHelper, "exportET")
                    .setHeader("siriDataType", simple("ET"))
                    .to("direct:anshar.export.snapshot.create.file")

                    .bean(exportHelper, "exportSX")
                    .setHeader("siriDataType", simple("SX"))
                    .to("direct:anshar.export.snapshot.create.file")

                    .bean(exportHelper, "exportVM")
                    .setHeader("siriDataType", simple("VM"))
                    .to("direct:anshar.export.snapshot.create.file")

                    .bean(exportHelper, "exportPT")
                    .setHeader("siriDataType", simple("PT"))
                    .to("direct:anshar.export.snapshot.create.file")
            ;

            from("direct:anshar.export.snapshot.create.file")
                    .setHeader(FILE_NAME, simple("${header.siriDataType}-${date:now:yyyy-MM-dd'T'HH:mm:ss.SSS}.xml"))
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .log("Writing file ${header." + FILE_NAME + "}")
                    .to("file:${header." + TMP_FOLDER + "}?fileName=${header." + FILE_NAME + "}")
                    .log("Written file ${header.CamelFileNameProduced}")
                    .to("direct:uploadBlob")
                    .routeId("anshar.export.snapshot.create.file")
            ;


            from("direct:uploadBlob")
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .bean("blobStoreService", "uploadBlob")
                    .setBody(simple(""))
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .routeId("blobstore-upload");
        } else {
            log.info("Uploading snapshot disabled");
        }
    }
}
