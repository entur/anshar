package no.rutebanken.anshar.messages.collections;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.Instant;
import java.util.Map;

import org.mapdb.*;


@Configuration
public class DistributedCollection {

    private static Logger logger = LoggerFactory.getLogger(DistributedCollection.class);
    private static HazelcastInstance hazelcastInstance;

    @Value("${anshar.expiry.period.seconds}")
    private int expiryPeriodSeconds = 30;

    private static DistributedCollection INSTANCE;

    static {
        INSTANCE = new DistributedCollection();
        Config config = new Config();
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    public static ExpiringConcurrentMap<String,PtSituationElement> getSituationsMap(){
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.situations"), INSTANCE.expiryPeriodSeconds);
    }
    public static ExpiringConcurrentMap<String, EstimatedVehicleJourney> getJourneysMap(){
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.journeys"), INSTANCE.expiryPeriodSeconds);
    }
    public static ExpiringConcurrentMap<String,VehicleActivityStructure> getVehiclesMap(){
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.vehicles"), INSTANCE.expiryPeriodSeconds);
    }
    public static ExpiringConcurrentMap<String, ProductionTimetableDeliveryStructure> getProductionTimetablesMap(){
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.productionTimetables"), INSTANCE.expiryPeriodSeconds);
    }

    public static Map<String,SubscriptionSetup> getActiveSubscriptionsMap() {
        return hazelcastInstance.getMap("anshar.subscriptions.active");
    }

    public static Map<String,SubscriptionSetup> getPendingSubscriptionsMap() {
        return hazelcastInstance.getMap("anshar.subscriptions.pending");
    }

    public static Map<String, Instant> getLastActivityMap() {
        return hazelcastInstance.getMap("anshar.activity.last");
    }

    public static Map<String, Instant> getActivatedTimestampMap() {
        return hazelcastInstance.getMap("anshar.activity.activated");
    }

    public static Map<String, Integer> getHitcountMap() {
        return hazelcastInstance.getMap("anshar.activity.hitcount");
    }
}
