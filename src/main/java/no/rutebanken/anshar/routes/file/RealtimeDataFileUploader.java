package no.rutebanken.anshar.routes.file;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private final static String TMP_FOLDER = "AnsharTmpFolder";
    final static String ZIP_FILE_PATH = "AnsharZipFilePATH";
    private final static String ZIP_FILE = "AnsharZipFile";

    protected RealtimeDataFileUploader(@Autowired AnsharConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (snapshotInterval <= 0) {
            log.info("Uploading snapshot disabled");
            return;
        }

        if (tmpFolder.endsWith("/")) {
            tmpFolder = tmpFolder.substring(0, tmpFolder.length()-1);
        }

        log.info("Uploading snapshot every {} minutes", snapshotInterval);
        singletonFrom("quartz2://anshar.export.snapshot?fireNow=true&trigger.repeatInterval=" + (snapshotInterval * 60 * 1000)
                ,"anshar.export.snapshot")
                .choice()
                .when(p -> isLeader())
                    .setHeader(TMP_FOLDER, simple(tmpFolder))
                    .setHeader(ZIP_FILE, simple("SIRI-SNAPSHOT-${date:now:yyyyMMdd-HHmmss}.zip"))
                    .setHeader(ZIP_FILE_PATH, simple( "${header."+TMP_FOLDER+"}/${header."+ZIP_FILE+"}"))
                    .log("Exporting snapshot to ${header."+ZIP_FILE+"}")

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

                    .to("direct:anshar.export.create.zip")
                    .to("direct:anshar.export.upload.zip")
                    .to("direct:anshar.export.delete.zip")
                .end()

        ;

        from("direct:anshar.export.snapshot.create.file")
                .setHeader(FILE_NAME, simple("${header.siriDataType}.xml"))
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .to("file:?fileName=${header." + TMP_FOLDER + "}/${header." + FILE_NAME + "}")
                .routeId("anshar.export.snapshot.create.file")
        ;

        from("direct:anshar.export.create.zip")
                .process(p -> {
                    zipFilesInFolder((String)p.getIn().getHeader(TMP_FOLDER), (String)p.getIn().getHeader(ZIP_FILE_PATH));
                })
                .log("Created ZIP: ${header." + ZIP_FILE_PATH + "}")
                .routeId("anshar.export.create.zip");

        from("direct:anshar.export.upload.zip")
                .bean("blobStoreService", "uploadBlob")
                .setBody(simple(""))
                .log("Snapshot ${header."+ZIP_FILE+"} uploaded.")
                .routeId("anshar.export.upload.zip");

        from("direct:anshar.export.delete.zip")
                .process(p -> {
                    File folder = new File((String)p.getIn().getHeader(TMP_FOLDER));
                    Arrays.stream(folder.listFiles(pathname -> pathname.getName().endsWith(".zip"))).forEach(file -> file.delete());
                })
                .routeId("anshar.export.delete.zip");
    }

    private boolean isLeader() {
        boolean isLeader = isLeader("anshar.export.snapshot");
        log.info("Is leader: {}", isLeader);
        return isLeader;
    }

    private static void zipFilesInFolder(String folder, String targetFilePath) {
        try {

            FileOutputStream out = new FileOutputStream(new File(targetFilePath));
            ZipOutputStream outZip = new ZipOutputStream(out);

            File fileFolder = new File(folder);
            Arrays.stream(fileFolder.listFiles())
                    .filter(file -> file.getName().endsWith(".xml"))
                    .forEach(file -> {
                            try {
                                addToZipFile(file, outZip);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

            outZip.close();
            out.close();
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to zip files in folder: " + ioe.getMessage(), ioe);
        }
    }

    private static void addToZipFile(File file, ZipOutputStream zos) throws IOException {
            FileInputStream fis = new FileInputStream(file);
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zos.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }

            zos.closeEntry();
            fis.close();
    }


}
