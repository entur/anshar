package no.rutebanken.anshar.routes.export.file;

import com.google.cloud.storage.Storage;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.InputStream;

@Repository
@Profile("gcs-blobstore")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Storage storage;

    @Value("${blobstore.gcs.container.name}")
    private String containerName;


    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        BlobStoreHelper.uploadBlob(storage, containerName, name, inputStream, makePublic);
    }
}
