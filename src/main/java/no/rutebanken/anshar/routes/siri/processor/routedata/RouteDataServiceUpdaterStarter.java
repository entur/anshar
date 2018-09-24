package no.rutebanken.anshar.routes.siri.processor.routedata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class RouteDataServiceUpdaterStarter {

    private static final Logger logger = LoggerFactory.getLogger(RouteDataServiceUpdaterStarter.class);

    @PostConstruct
    synchronized static void initializeUpdater() {
        logger.info("Starts the NeTEx updater service");
        //TODO: Should start gtfs/netex updater service based on config (or we should delete the old GTFS one and the need for this starter class)...
        NetexUpdaterService.initializeUpdater();
    }
}
