package no.rutebanken.anshar.messages.collections;

import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ExtendedKubernetesService extends KubernetesService {
    private static final Logger log = LoggerFactory.getLogger(ExtendedKubernetesService.class);

    public ExtendedKubernetesService(@Autowired AnsharConfiguration cfg) {
        super(cfg.getKubernetesUrl(), cfg.getNamespace(), cfg.isKubernetesEnabled());
    }

}