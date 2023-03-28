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

import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("gcs-blobstore")
public class GcsStorageConfig {

    private static final int CONNECT_AND_READ_TIMEOUT = 60000;

    @Value("${blobstore.gcs.credential.path:#{null}}")
    private String credentialPath;

    @Value("${blobstore.gcs.project.id}")
    private String projectId;

    @Bean
    public Storage storage() {
        if (credentialPath == null || credentialPath.isEmpty()) {
            // Use Default gcp credentials
            // Todo update rutebanken helpler dependency to use BlobStoreHelper.getStorage(projectId)
            return getStorage(projectId);
        }
        return BlobStoreHelper.getStorage(credentialPath, projectId);
    }

    private Storage getStorage(String projectId) {
        try {
            HttpTransportOptions transportOptions = StorageOptions.getDefaultHttpTransportOptions();
            transportOptions = transportOptions.toBuilder().setConnectTimeout(CONNECT_AND_READ_TIMEOUT).setReadTimeout(CONNECT_AND_READ_TIMEOUT)
                    .build();

            return StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setTransportOptions(transportOptions)
                    .build().getService();
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }


}
