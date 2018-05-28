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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@Service
public class BlobStoreService {

	@Autowired
	private BlobStoreRepository repository;

	@Autowired
	private Storage storage;

	@Value("${blobstore.gcs.container.name}")
	private String containerName;

	@PostConstruct
	public void init() {
		repository.setStorage(storage);
		repository.setContainerName(containerName);
	}

	public void uploadBlob(@Header(value = RealtimeDataFileUploader.ZIP_FILE_PATH) String path) throws FileNotFoundException {
		File f = new File(path);
		repository.uploadBlob(f.getName(), new BufferedInputStream(new FileInputStream(f)), true);
	}
}
