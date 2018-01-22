package no.rutebanken.anshar.routes.file;

import com.google.cloud.storage.Storage;

import java.io.InputStream;

public interface BlobStoreRepository {

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic);

    void setStorage(Storage storage);

    void setContainerName(String containerName);


}
