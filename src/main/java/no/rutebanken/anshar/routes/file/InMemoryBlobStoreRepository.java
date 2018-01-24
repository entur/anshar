package no.rutebanken.anshar.routes.file;

import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.InputStream;

@Repository
@Profile("in-memory-blobstore")
public class InMemoryBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic) {
        logger.info("blob with name {} ignored for in-memory-blobstore", objectName);
    }

    @Override
    public void setStorage(Storage storage) {

    }

    @Override
    public void setContainerName(String containerName) {

    }

}
