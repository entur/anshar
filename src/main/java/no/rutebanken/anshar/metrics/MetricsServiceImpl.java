package no.rutebanken.anshar.metrics;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.google.common.base.Strings;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class MetricsServiceImpl implements MetricsService {
    private static final Logger logger = LoggerFactory.getLogger(MetricsServiceImpl.class);

    private final MetricRegistry metrics = new MetricRegistry();

    private final GraphiteReporter reporter;

    private final Graphite graphite;

    private final Object LOCK = new Object();

    public MetricsServiceImpl(String graphiteServerDns, int graphitePort) {

        if (Strings.isNullOrEmpty(graphiteServerDns)) {
            throw new IllegalArgumentException("graphiteServerDns must not be null or empty");
        }

        logger.info("Setting up metrics service with graphite server dns: {}", graphiteServerDns);
        graphite = new Graphite(new InetSocketAddress(graphiteServerDns, graphitePort));

        reporter = GraphiteReporter.forRegistry(metrics)
                .prefixedWith("app.anshar")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
    }

    @PostConstruct
    public void postConstruct() {
        logger.info("Starting graphite reporter");
        reporter.start(10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() throws IOException {
        reporter.stop();
        if (graphite.isConnected()) {
            graphite.flush();
            graphite.close();
        }
    }


    @Override
    public void registerIncomingData(SiriDataType subscriptionType, String agencyId, int count) {
        String counterName = "data.from." + agencyId + ".type." + subscriptionType;
//        synchronized (LOCK) {
             metrics.meter(counterName).mark(count);

//            //Immediately report only the updated Meter
//            reporter.report(
//                    metrics.getGauges(),
//                    metrics.getCounters(),
//                    metrics.getHistograms(),
//                    metrics.getMeters((name, metric) -> counterName.equals(name)),
//                    metrics.getTimers());
//        }
    }

}
