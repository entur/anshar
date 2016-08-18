package no.rutebanken.anshar.messages;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.Instant;
import java.util.Map;

public class DistributedCollection {

    private static HazelcastInstance hazelcastInstance;

    static {

        Config config = new Config();
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    public static Map<String,PtSituationElement> getSituationsMap(){
        return hazelcastInstance.getMap("anshar.situations");
    }
    public static Map<String, EstimatedTimetableDeliveryStructure> getJourneysMap(){
        return hazelcastInstance.getMap("anshar.journeys");
    }
    public static Map<String,VehicleActivityStructure> getVehiclesMap(){
        return hazelcastInstance.getMap("anshar.vehicles");
    }
    public static Map<String, ProductionTimetableDeliveryStructure> getProductionTimetablesMap(){
        return hazelcastInstance.getMap("anshar.productionTimetables");
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
