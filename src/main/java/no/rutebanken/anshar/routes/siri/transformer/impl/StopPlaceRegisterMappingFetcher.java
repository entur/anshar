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

package no.rutebanken.anshar.routes.siri.transformer.impl;

import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

@Service
public class StopPlaceRegisterMappingFetcher {

    private static final Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMappingFetcher.class);

    private static final String ET_CLIENT_ID_HEADER = "ET-Client-ID";

    private static final String ET_CLIENT_NAME_HEADER = "ET-Client-Name";

    private static final String ET_CLIENT_NAME = "anshar";

    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;

    @Value("${anshar.mapping.stopplaces.update.timeout.read.ms:40000}")
    private int readTimeoutMs = 40000;

    @Value("${anshar.mapping.stopplaces.update.timeout.connect.ms:5000}")
    private int connectTimeoutMs = 5000;

    @Value("${HOSTNAME:anshar}")
    private String clientId;


    public Map<String, String> fetchStopPlaceMapping(String mappingUrl) throws IOException {

        Map<String, String> stopPlaceMappings = new HashMap<>();
        if (mappingUrl != null && !mappingUrl.isEmpty()) {

            long t1 = System.currentTimeMillis();

            URL url = new URL(mappingUrl);

            Counter duplicates = new CounterImpl(0);

            URLConnection connection = url.openConnection();
            connection.setRequestProperty(ET_CLIENT_ID_HEADER, clientId);
            connection.setRequestProperty(ET_CLIENT_NAME_HEADER, ET_CLIENT_NAME);

            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            reader.lines().forEach(line -> {
                StringTokenizer tokenizer = new StringTokenizer(line, ",");
                String id = tokenizer.nextToken();
                String generatedId = tokenizer.nextToken();

                if (stopPlaceMappings.containsKey(id)) {
                    duplicates.increment();
                }
                stopPlaceMappings.put(id, generatedId);
            });

            long t2 = System.currentTimeMillis();


            logger.info("Fetched mapping data - {} mappings, found {} duplicates. [fetched:{}ms]", stopPlaceMappings.size(), duplicates.getValue(), (t2 - t1));
            return stopPlaceMappings;
        }
        logger.error("URL is null or empty. Not possible to fetch mapping from stop place register: {}", mappingUrl);
        return stopPlaceMappings;
    }


}
