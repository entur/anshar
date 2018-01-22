package no.rutebanken.anshar.routes.file;

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
	BlobStoreRepository repository;

	@Autowired
	Storage storage;

	@Value("${blobstore.gcs.container.name}")
	String containerName;

	@PostConstruct
	public void init() {
		repository.setStorage(storage);
		repository.setContainerName(containerName);
	}

	public void uploadBlob(@Header(value = RealtimeDataFileUploader.ZIP_FILE_PATH) String path) throws FileNotFoundException {
		File f = new File(path);
		repository.uploadBlob(f.getName(), new BufferedInputStream(new FileInputStream(f)), false);
	}
}
