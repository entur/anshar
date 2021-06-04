package no.rutebanken.anshar.data.collections;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KubernetesService extends org.rutebanken.hazelcasthelper.service.KubernetesService {

    public KubernetesService(
        @Value("${anshar.hazelcast.kubernetes.serviceName:}") String serviceName,
        @Value("${anshar.hazelcast.kubernetes.namespace:}") String namespace,
        @Value("${anshar.hazelcast.kubernetes.enabled:false}") boolean isKubernetesEnabled
    ) {
        super(null, serviceName, namespace, isKubernetesEnabled);
    }

}
