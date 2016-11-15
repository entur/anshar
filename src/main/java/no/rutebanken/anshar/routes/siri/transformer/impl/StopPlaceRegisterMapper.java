package no.rutebanken.anshar.routes.siri.transformer.impl;


import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

@Configuration
public class StopPlaceRegisterMapper extends ValueAdapter {

    private static Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMapper.class);

    private static Map<String, String> stopPlaceMappings;
    private static IMap<String, Instant> lockMap;
    private static boolean attemptedToInitialize = false;

    @Value("${anshar.mapping.stopplaces.url}")
    private String stopPlaceMappingUrl = "http://tiamat:8777/jersey/id_mapping";

    private List<String> prefixes;

    static {
        DistributedCollection dc = new DistributedCollection();
        stopPlaceMappings = dc.getStopPlaceMappings();
        lockMap = dc.getLockMap();
    }

    public StopPlaceRegisterMapper() {
        //called by Spring during startup
    }

    public StopPlaceRegisterMapper(Class clazz, List<String> prefixes) {
        super(clazz);
        this.prefixes = prefixes;
        initialize();
    }


    public String apply(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        if (prefixes != null && !prefixes.isEmpty()) {
            for (String prefix : prefixes) {
                String mappedValue = stopPlaceMappings.get(createCompleteId(prefix, id));
                if (mappedValue != null) {
                    return mappedValue;
                }
            }
        }

        String mappedValue = stopPlaceMappings.get(id);
        return mappedValue != null ? mappedValue : id;
    }

    private String createCompleteId(String prefix, String id) {
        return new StringBuilder().append(prefix).append(":").append("StopArea").append(":").append(id).toString();
    }

    //Used for testing
    public static void setStopPlaceMappings(Map<String, String> stopPlaceMappings) {
        StopPlaceRegisterMapper.stopPlaceMappings = stopPlaceMappings;
    }

    @Bean
    protected boolean initialize() {
        lockMap.lock("Initializing");
        try {
            if (stopPlaceMappings.isEmpty() & !attemptedToInitialize) {

                // Data is not initialized, and no attempt has been made
                // Results in only one attempt during startup

                attemptedToInitialize = true;
//                logger.info("Initializing data - start. Fetching mapping-data from {}", stopPlaceMappingUrl);
//                URL url = new URL(stopPlaceMappingUrl);
//                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));

                logger.info("Initializing data - start. Fetching mapping-data from internal file ");
                ClassLoader classLoader = getClass().getClassLoader();
                BufferedReader in = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream("id_mapping.csv")));

                String inputLine;
                int duplicates = 0;
                while ((inputLine = in.readLine()) != null) {
                    StringTokenizer tokenizer = new StringTokenizer(inputLine, ",");
                    String id = tokenizer.nextToken(); //First token
                    String generatedId = tokenizer.nextToken().replaceAll(":", ".");

                    if (stopPlaceMappings.containsKey(id)) {
                        duplicates++;
                    }
                    stopPlaceMappings.put(id, generatedId);
                }
                in.close();
                logger.info("Initializing data - done - {} mappings, found {} duplicates.", stopPlaceMappings.size(), duplicates);
            } else {
                logger.trace("Initializing data - already initialized.");
            }
        } catch (IOException e) {
            logger.error("Initializing data - caused exception", e);
        } finally {
            lockMap.unlock("Initializing");
        }
        return true;
    }
}
