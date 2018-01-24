package no.rutebanken.anshar.routes.file;

import com.google.cloud.storage.Storage;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("gcs-blobstore")
public class GcsStorageConfig {

    @Value("${blobstore.gcs.credential.path}")
    private String credentialPath;

    @Value("${blobstore.gcs.project.id}")
    private String projectId;

    @Bean
    public Storage storage() {
        return BlobStoreHelper.getStorage(credentialPath, projectId);
    }


}
