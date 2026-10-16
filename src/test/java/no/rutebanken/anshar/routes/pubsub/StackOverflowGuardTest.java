package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StackOverflowGuardTest {

    private CamelContext camelContext;

    @AfterEach
    public void tearDown() {
        camelContext.stop();
    }

    /**
     * Camel does not handle Errors thrown by processors - the exchange is never completed, and stays inflight forever.
     */
    @Test
    public void testStackOverflowFailsExchangeInsteadOfLeavingItInflight() throws Exception {
        startRouteWith(StackOverflowGuard.guard(p -> recurse(0)));

        CompletableFuture<Object> result = camelContext.createProducerTemplate()
                .asyncRequestBodyAndHeader("direct:guarded", "body", "header", "value");

        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        Throwable cause = failure.getCause().getCause();
        assertInstanceOf(StackOverflowGuard.StackOverflowException.class, cause);
        assertInstanceOf(StackOverflowError.class, cause.getCause());
        assertTrue(cause.getCause().getStackTrace().length <= StackOverflowGuard.MAX_STACK_TRACE_DEPTH);

        assertEquals(0, camelContext.getInflightRepository().size());
    }

    @Test
    public void testExchangesWithoutErrorsAreProcessedAsBefore() throws Exception {
        startRouteWith(StackOverflowGuard.guard(p -> p.getMessage().setBody("processed")));

        Object result = camelContext.createProducerTemplate()
                .asyncRequestBodyAndHeader("direct:guarded", "body", "header", "value")
                .get(5, TimeUnit.SECONDS);

        assertEquals("processed", result);
        assertEquals(0, camelContext.getInflightRepository().size());
    }

    private void startRouteWith(Processor processor) throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:guarded")
                        .choice().when(body().isNotNull())
                            .process(processor)
                        .end();
            }
        });
        camelContext.start();
    }

    private static int recurse(int depth) {
        return recurse(depth + 1) + 1;
    }
}
