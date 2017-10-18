package no.rutebanken.anshar.subscription;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@PropertySource(value = "classpath:subscriptions-${anshar.environment}.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "anshar", ignoreInvalidFields=false)
@Configuration
public class SubscriptionConfig {

    private List<SubscriptionSetup> subscriptions;

    public List<SubscriptionSetup> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionSetup> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
