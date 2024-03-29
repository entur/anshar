/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

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
        logger.info("Setting storage: {}", storage);
        this.storage = storage;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public void uploadBlob(String name, byte[] bytes) {
        logger.info("Uploading file {} to container {}", name, containerName);
        BlobStoreHelper.uploadBlob(storage, containerName, name, bytes, false);
    }

    @Override
    public InputStream getBlob(String name) {
        logger.info("Downloading file {} from container {} and storage: {}", name, containerName, storage);
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }
}
