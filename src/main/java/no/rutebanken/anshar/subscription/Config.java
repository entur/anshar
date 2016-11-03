package no.rutebanken.anshar.subscription;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConfigurationProperties(locations = "classpath:subscriptions.yml", prefix = "anshar", ignoreInvalidFields=false)
@Configuration
public class Config {

    private List<SubscriptionSetup> subscriptions;

    public List<SubscriptionSetup> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionSetup> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
