package no.rutebanken.anshar.routes.health;

import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.collections.HealthCheckKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;

@Service
public class HealthManager {

    private Logger logger = LoggerFactory.getLogger(HealthManager.class);

    @Autowired
    @Qualifier("getHealthCheckMap")
    private IMap<Enum<HealthCheckKey>, Instant> healthCheckMap;

    @PostConstruct
    private void initialize() {
        if (!healthCheckMap.containsKey(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA)) {
            healthCheckMap.put(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA, Instant.now());
        }
    }

    public boolean isHazelcastAlive() {
        try {
            healthCheckMap.put(HealthCheckKey.NODE_LIVENESS_CHECK, Instant.now());
            return healthCheckMap.containsKey(HealthCheckKey.NODE_LIVENESS_CHECK);
        } catch (HazelcastInstanceNotActiveException e) {
            logger.warn("HazelcastInstance not active - ", e);
            return false;
        }
    }

    @Bean
    public Instant serverStartTime() {
        if (!healthCheckMap.containsKey(HealthCheckKey.SERVER_START_TIME)) {
            healthCheckMap.put(HealthCheckKey.SERVER_START_TIME, Instant.now());
        }
        return healthCheckMap.get(HealthCheckKey.SERVER_START_TIME);
    }

    public void dataReceived() {
        healthCheckMap.put(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA, Instant.now());
    }


    public long getSecondsSinceDataReceived() {
        Instant lastReceivedData = healthCheckMap.get(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA);
        long lastReceivedMillis = lastReceivedData.toEpochMilli();

        long minutes = (Instant.now().toEpochMilli() - lastReceivedMillis)/(1000);
        logger.info("Last received data: {}, {} minutes ago", lastReceivedData, minutes);
        return minutes;
    }
}
