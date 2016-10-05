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

    @Value("${anshar.hazelcast.enable}")
    private static boolean useHazelCast = false;

    @Value("${anshar.expiry.period.seconds}")
    private static int expiryPeriodSeconds = 30;

    private static DB db;

    static {
        if (useHazelCast) {
            Config config = new Config();
            hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        } else {
            db = DBMaker
                    .tempFileDB()
                    .fileMmapEnable()
                    .make();

            db.getStore().getAllFiles().forEach(s ->
                logger.info("Creating MapDB tmp-file at " + s )
            );
        }
    }

    public static ExpiringConcurrentMap<String,PtSituationElement> getSituationsMap(){
        if (!useHazelCast) {
            return new ExpiringConcurrentMap<>(db.hashMap("anshar.situations", Serializer.STRING, Serializer.JAVA).createOrOpen(), expiryPeriodSeconds);
        }
        return  new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.situations"), expiryPeriodSeconds);
    }
    public static ExpiringConcurrentMap<String, EstimatedVehicleJourney> getJourneysMap(){
        if (!useHazelCast) {
            return new ExpiringConcurrentMap<>(db.hashMap("anshar.journeys", Serializer.STRING, Serializer.JAVA).createOrOpen(), expiryPeriodSeconds);
        }
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.journeys"), expiryPeriodSeconds);
    }
    public static ExpiringConcurrentMap<String,VehicleActivityStructure> getVehiclesMap(){
        if (!useHazelCast) {
            return new ExpiringConcurrentMap<>(db.hashMap("anshar.vehicles", Serializer.STRING, Serializer.JAVA).createOrOpen(), expiryPeriodSeconds);
        }
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.vehicles"), expiryPeriodSeconds);
    }
    public static ExpiringConcurrentMap<String, ProductionTimetableDeliveryStructure> getProductionTimetablesMap(){
        if (!useHazelCast) {
            return new ExpiringConcurrentMap<>(db.hashMap("anshar.productionTimetables", Serializer.STRING, Serializer.JAVA).createOrOpen(), expiryPeriodSeconds);
        }
        return new ExpiringConcurrentMap<>(hazelcastInstance.getMap("anshar.productionTimetables"), expiryPeriodSeconds);
    }

    public static Map<String,SubscriptionSetup> getActiveSubscriptionsMap() {
        if (!useHazelCast) {
            return db.hashMap("anshar.subscriptions.active", Serializer.STRING, Serializer.JAVA).createOrOpen();
        }
        return hazelcastInstance.getMap("anshar.subscriptions.active");
    }

    public static Map<String,SubscriptionSetup> getPendingSubscriptionsMap() {
        if (!useHazelCast) {
            return db.hashMap("anshar.subscriptions.pending", Serializer.STRING, Serializer.JAVA).createOrOpen();
        }
        return hazelcastInstance.getMap("anshar.subscriptions.pending");
    }

    public static Map<String, Instant> getLastActivityMap() {
        if (!useHazelCast) {
            return db.hashMap("anshar.activity.last", Serializer.STRING, Serializer.JAVA).createOrOpen();
        }
        return hazelcastInstance.getMap("anshar.activity.last");
    }

    public static Map<String, Instant> getActivatedTimestampMap() {
        if (!useHazelCast) {
            return db.hashMap("anshar.activity.activated", Serializer.STRING, Serializer.JAVA).createOrOpen();
        }
        return hazelcastInstance.getMap("anshar.activity.activated");
    }

    public static Map<String, Integer> getHitcountMap() {
        if (!useHazelCast) {
            return db.hashMap("anshar.activity.hitcount", Serializer.STRING, Serializer.INTEGER).createOrOpen();
        }
        return hazelcastInstance.getMap("anshar.activity.hitcount");
    }
}
