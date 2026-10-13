package no.rutebanken.anshar.routes.validation;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SiriXmlValidatorTest extends SpringBootBaseTest {

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    @Qualifier("getValidationResultRefMap")
    private IMap<String, List<String>> validationResultRefs;

    @Autowired
    @Qualifier("getValidationResultSiriMap")
    private IMap<String, byte[]> validatedSiri;

    @Autowired
    @Qualifier("getValidationSizeTracker")
    private IMap<String, Long> validationSize;

    @Autowired
    @Qualifier("getValidationResultJsonMap")
    private IMap<String, JSONObject> validationResults;

    @Test
    public void testReferencesToExpiredResultsAreRemoved() {
        // "expired" refs have no result
        validatedSiri.set("partly-kept", new byte[10]);
        validationResults.set("partly-kept", new JSONObject());
        validationResultRefs.set("partly-expired-subscription", List.of("partly-expired", "partly-kept"));
        validationSize.set("partly-expired-subscription", 30L);

        validatedSiri.set("fully-expired", new byte[20]);
        validationResultRefs.set("fully-expired-subscription", List.of("fully-expired"));
        validationSize.set("fully-expired-subscription", 20L);

        siriXmlValidator.removeExpiredValidationResults();

        assertEquals(List.of("partly-kept"), validationResultRefs.get("partly-expired-subscription"));
        assertEquals(10L, validationSize.get("partly-expired-subscription"));

        assertFalse(validationResultRefs.containsKey("fully-expired-subscription"));
        assertFalse(validationSize.containsKey("fully-expired-subscription"));
        assertFalse(validatedSiri.containsKey("fully-expired"));

        siriXmlValidator.clearValidationResults("partly-expired-subscription");
    }
}
