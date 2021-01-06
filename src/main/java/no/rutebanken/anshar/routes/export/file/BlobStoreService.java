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
import org.apache.camel.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@Service
public class BlobStoreService {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private BlobStoreRepository repository;

	@Autowired
	private Storage storage;

	@Value("${blobstore.gcs.container.name}")
	private String containerName;

	@PostConstruct
	public void init() {
		logger.info("Initializing BlobStoreService with container {}, storage {}", containerName, storage);
		repository.setStorage(storage);
		repository.setContainerName(containerName);
	}

	public void uploadBlob(@Header(value = RealtimeDataFileUploader.ZIP_FILE_PATH) String path) throws IOException {
		File f = new File(path);
		repository.uploadBlob(f.getName(), Files.readAllBytes(f.toPath()));
	}

	public void uploadBlob(String name, byte[] data) {
		uploadBlob(name, data);
	}

	public void uploadBlob(String name, byte[] data, boolean makePublic) {
		if (data != null) {
			repository.uploadBlob(name, data);
		}
	}

	public InputStream getBlob(String name) {
		return repository.getBlob(name);
	}
}
