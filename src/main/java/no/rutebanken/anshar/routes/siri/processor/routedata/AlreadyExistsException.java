package no.rutebanken.anshar.routes.siri.processor.routedata;

public class AlreadyExistsException extends Throwable {

        private final String msg;

        public AlreadyExistsException(
            String serviceJourneyId
        ) {
            this.msg = "Trip with id " + serviceJourneyId + " already exists";
        }

        @Override
        public String getMessage() {
            return msg;
        }
    }