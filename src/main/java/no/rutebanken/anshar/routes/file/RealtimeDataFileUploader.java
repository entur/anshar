package no.rutebanken.anshar.routes.file;

import no.rutebanken.anshar.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.CamelConfiguration;
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
    private String TMP_FOLDER = "AnsharTmpFolder";
    final static String ZIP_FILE_PATH = "AnsharZipFilePATH";
    final static String ZIP_FILE = "AnsharZipFile";

    protected RealtimeDataFileUploader(@Autowired CamelConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (snapshotInterval > 0) {
            log.info("Uploading snapshot every {} minutes", snapshotInterval);
            singletonFrom("quartz2://anshar.export.snapshot?fireNow=true&trigger.repeatInterval=" + (snapshotInterval * 60 * 1000)
                    ,"anshar.export.snapshot")
                    .setHeader(TMP_FOLDER, simple(tmpFolder + "/${date:now:yyyyMMdd-HHmmss}/"))
                    .setHeader(ZIP_FILE, simple("SIRI-SNAPSHOT-${date:now:yyyyMMdd-HHmmss}.zip"))
                    .setHeader(ZIP_FILE_PATH, simple( "${header."+TMP_FOLDER+"}/${header."+ZIP_FILE+"}"))
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

                    .to("direct:anshar.zip.folder")
                    .to("direct:anshar.upload.zip")
                    .to("direct:anshar.delete.folder")

            ;

            from("direct:anshar.export.snapshot.create.file")
                    .setHeader(FILE_NAME, simple("${header.siriDataType}.xml"))
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .to("file:?fileName=${header." + TMP_FOLDER + "}/${header." + FILE_NAME + "}")
                    .routeId("anshar.export.snapshot.create.file")
            ;


            from("direct:anshar.zip.folder")
                    .process(p -> {
                        zipFilesInFolder((String)p.getIn().getHeader(TMP_FOLDER), (String)p.getIn().getHeader(ZIP_FILE_PATH));
                    })
                    .routeId("anshar.zip.folder");

            from("direct:anshar.upload.zip")
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .bean("blobStoreService", "uploadBlob")
                    .setBody(simple(""))
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .routeId("anshar.upload.zip");


            from("direct:anshar.delete.folder")
                    .process(p -> {
                        File folder = new File((String)p.getIn().getHeader(TMP_FOLDER));
                        Arrays.stream(folder.listFiles()).forEach(file -> file.delete());
                        boolean deleted = folder.delete();
                    })
                    .routeId("anshar.delete.folder");
        } else {
            log.info("Uploading snapshot disabled");
        }
    }

    public static File zipFilesInFolder(String folder, String targetFilePath) {
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

            return new File(targetFilePath);
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
