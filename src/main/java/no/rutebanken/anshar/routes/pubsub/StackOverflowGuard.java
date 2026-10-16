package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.Processor;

import java.util.Arrays;

/**
 * Camel only handles Exceptions thrown by processors. An Error leaves the exchange inflight forever - and makes
 * graceful shutdown wait for it until the shutdown timeout.
 * Deeply nested SIRI-objects can overflow the stack when serialized, so the StackOverflowError is rethrown as an
 * Exception to fail the exchange and let the error handler log it.
 */
public final class StackOverflowGuard {

    // The recursion repeats itself, so the top of the stack is enough to see where it happens
    static final int MAX_STACK_TRACE_DEPTH = 50;

    private StackOverflowGuard() {
    }

    public static Processor guard(Processor processor) {
        return exchange -> {
            try {
                processor.process(exchange);
            } catch (StackOverflowError e) {
                e.setStackTrace(Arrays.copyOf(e.getStackTrace(), Math.min(e.getStackTrace().length, MAX_STACK_TRACE_DEPTH)));
                throw new StackOverflowException(e);
            }
        };
    }

    public static class StackOverflowException extends RuntimeException {
        StackOverflowException(StackOverflowError cause) {
            super("StackOverflowError when processing exchange", cause);
        }
    }
}
