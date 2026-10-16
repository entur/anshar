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

package no.rutebanken.anshar.config;

import org.apache.camel.spring.boot.CamelConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelShutdownConfigurationTest {

    /**
     * Pods get 30-45s from SIGTERM to SIGKILL (60s grace period minus a 15-30s preStop sleep).
     * Camel must give up early enough for Hazelcast to leave the cluster gracefully afterwards - a member that is
     * killed before leaving stalls writes on the remaining members until it is detected as dead.
     */
    @Test
    public void camelShutdownLeavesTimeForHazelcastToLeaveTheCluster() throws IOException {
        CamelConfigurationProperties camel = bindCamelProperties(Path.of("src/main/resources/application.properties"));

        assertEquals(20, camel.getMain().getShutdownTimeout());
        // Logs the exchanges still in flight when the timeout is reached
        assertTrue(camel.getMain().isShutdownLogInflightExchangesOnTimeout());
    }

    /**
     * Without browsing, only counts per route are tracked - and neither the shutdown log nor CamelInflightMonitor can
     * tell where the exchanges are stuck.
     */
    @Test
    public void inflightExchangesCanBeBrowsed() throws IOException {
        CamelConfigurationProperties camel = bindCamelProperties(Path.of("src/main/resources/application.properties"));

        assertTrue(camel.getMain().isInflightRepositoryBrowseEnabled());
    }

    // The test classpath has its own application.properties, so the main one is read from file
    private static CamelConfigurationProperties bindCamelProperties(Path applicationProperties) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(applicationProperties)) {
            properties.load(reader);
        }
        CamelConfigurationProperties camel = new CamelConfigurationProperties();
        new Binder(new MapConfigurationPropertySource(properties))
                .bind("camel", Bindable.ofInstance(camel));
        return camel;
    }
}
