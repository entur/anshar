package no.rutebanken.anshar.metrics;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsServiceConfig {

    private static final Logger logger = LoggerFactory.getLogger(MetricsServiceConfig.class);

    @Bean
    public MetricsService metricsService(@Value("${graphite.server:}") String graphiteServerDns) {

        if(Strings.isNullOrEmpty(graphiteServerDns)) {
            logger.info("Not starting metrics service, as I was not supplied with graphite server dns name");
            return new DoNothingMetricsService();
        }

        return new MetricsServiceImpl(graphiteServerDns, 2003);
    }
}