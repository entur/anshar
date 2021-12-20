package no.rutebanken.anshar.routes;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous processor that aborts execution after a defined timeout
 *
 * https://coderedirect.com/questions/418453/apache-camel-timeout-synchronous-route
 */
public class TimeOutProcessor implements Processor {

    private final Logger logger = LoggerFactory.getLogger(TimeOutProcessor.class);

    private String route;
    private long timeoutMillis;

    public TimeOutProcessor(String route, long timeoutMillis) {
        this.route = route;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        MDC.put(Exchange.BREADCRUMB_ID, exchange.getIn().getHeader(Exchange.BREADCRUMB_ID));

        Future<Exchange> future = null;
        ProducerTemplate producerTemplate = exchange.getFromEndpoint().getCamelContext().createProducerTemplate();
        try {

            future = producerTemplate.asyncRequestBodyAndHeaders(route, exchange.getIn().getBody(), exchange.getIn().getHeaders(), Exchange.class);
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
            exchange.getMessage().setBody(future.get(timeoutMillis, TimeUnit.MILLISECONDS));
            producerTemplate.stop();
            future.cancel(true);
        } catch (TimeoutException e) {
            logger.info("Request timed out after " + timeoutMillis + " ms - aborted.", e);
            producerTemplate.stop();
            future.cancel(true);
            throw new TimeoutException("Caught timeout while processing route: " + route);
        } finally {
            MDC.remove(Exchange.BREADCRUMB_ID);
        }

    }
}