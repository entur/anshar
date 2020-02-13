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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Repository
@Profile("in-memory-blobstore")
public class InMemoryBlobStoreRepository implements BlobStoreRepository {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void uploadBlob(String objectName, byte[] bytes, boolean makePublic) {
        logger.info("blob with name {}, size {} ignored for in-memory-blobstore", objectName, bytes.length);
    }

    @Override
    public void setStorage(Storage storage) {

    }

    @Override
    public void setContainerName(String containerName) {

    }

    @Override
    public InputStream getBlob(String name) {
        try {
            return new FileInputStream(name);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

}
