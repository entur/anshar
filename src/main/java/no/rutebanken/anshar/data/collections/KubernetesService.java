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
