package no.rutebanken.anshar.routes;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous processor that aborts execution after a defined timeout
 *
 * https://coderedirect.com/questions/418453/apache-camel-timeout-synchronous-route
 */
public class TimeOutProcessor implements Processor {

    private String route;
    private long timeoutMillis;

    public TimeOutProcessor(String route, long timeoutMillis) {
        this.route = route;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Future<Exchange> future = null;
        ProducerTemplate producerTemplate = exchange.getFromEndpoint().getCamelContext().createProducerTemplate();
        try {

            future = producerTemplate.asyncSend(route, exchange);
            exchange.getIn().setBody(future.get(timeoutMillis, TimeUnit.MILLISECONDS));
            producerTemplate.stop();
            future.cancel(true);
        } catch (TimeoutException e) {
            producerTemplate.stop();
            future.cancel(true);
            throw new TimeoutException("Caught timeout while processing route: " + route);
        }

    }
}