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

package no.rutebanken.anshar.data;

import com.hazelcast.map.IMap;
import jakarta.validation.constraints.NotNull;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class RequestorRefRepository {

    @Autowired
    private IMap<String[], RequestorRefStats> requestorRefs;

    @Autowired
    private AnsharConfiguration configuration;

    public void touchRequestorRef(@NotNull String requestorRef, String datasetId, String clientTrackingName, @NotNull SiriDataType dataType) {

        String[] key = createKey(requestorRef, dataType);

        RequestorRefStats stats = requestorRefs.get(key);

        if (stats == null) {
            stats = new RequestorRefStats(requestorRef, datasetId, clientTrackingName, dataType);
        }

        stats.touch(Instant.now());

        requestorRefs.set(key, stats, configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
    }

    private String[] createKey(@NotNull String requestorRef, @NotNull SiriDataType dataType) {
        return new String[]{requestorRef, dataType.name()};
    }

    public RequestorRefStats getStats(String requestorRef, SiriDataType dataType) {
        return requestorRefs.get(createKey(requestorRef, dataType));
    }
}
