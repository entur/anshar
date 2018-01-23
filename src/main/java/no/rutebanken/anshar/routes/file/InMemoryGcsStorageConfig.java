package no.rutebanken.anshar.routes.file;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("in-memory-blobstore")
public class InMemoryGcsStorageConfig {

    @Bean
    public Storage storage() {
        return getStorage();
    }

    @Bean
    public Storage exchangeStorage() {
        return getStorage();
    }

    @Bean
    public Storage otpReportStorage() {
        return getStorage();
    }

    private Storage getStorage() {
        return StorageOptions.newBuilder().setProjectId("1234").build().getService();
    }

}
