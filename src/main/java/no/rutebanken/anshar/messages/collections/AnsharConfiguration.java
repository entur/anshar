package no.rutebanken.anshar.messages.collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnsharConfiguration {

    @Value("${rutebanken.kubernetes.url:}")
    private String kubernetesUrl;

    @Value("${rutebanken.kubernetes.enabled:true}")
    private boolean kuberentesEnabled;

    @Value("${rutebanken.kubernetes.namespace:default}")
    private String namespace;

    public String getKubernetesUrl() {
        return kubernetesUrl;
    }

    public boolean isKuberentesEnabled() {
        return kuberentesEnabled;
    }

    public String getNamespace() {
        return namespace;
    }
}
