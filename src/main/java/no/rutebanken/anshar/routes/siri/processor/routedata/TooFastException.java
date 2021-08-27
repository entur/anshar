package no.rutebanken.anshar.routes.siri.processor.routedata;

public class TooFastException extends Throwable {

        private final String msg;

        public TooFastException(
            String fromStop, String toStop, int kph
        ) {
            this.msg = "Too fast (" + kph + " kph) between " + fromStop + " and " + toStop;
        }

        @Override
        public String getMessage() {
            return msg;
        }
    }