package no.rutebanken.anshar.routes.siri.processor;

import uk.org.siri.siri20.Siri;

public interface PostProcessor {

    /**
     * Is called after all ValueAdapters.
     * Should be used for any for manual post-processing needed for specific values -
     * e.g. creating a Quay reference based on StopPoint AND platform
     * @param siri
     */
    void process(Siri siri);
}
