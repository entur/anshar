package no.rutebanken.anshar.routes.validation;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testDefaultProfileIsUsedWhenSubscriptionHasNoProfile() {
        SubscriptionSetup subscription = new SubscriptionSetup();

        // Test config sets anshar.validation.profile.name=test, which has no validators
        assertEquals("test", siriXmlValidator.resolveValidationProfile(subscription));
        assertTrue(siriXmlValidator.getValidationRules("test").isEmpty());

        subscription.setValidationProfile("  ");
        assertEquals("test", siriXmlValidator.resolveValidationProfile(subscription));
    }

    @Test
    public void testSubscriptionProfileOverridesDefault() {
        SubscriptionSetup norway = new SubscriptionSetup();
        norway.setValidationProfile("norway");

        SubscriptionSetup sweden = new SubscriptionSetup();
        sweden.setValidationProfile(" Sweden ");

        assertEquals("norway", siriXmlValidator.resolveValidationProfile(norway));
        assertEquals("sweden", siriXmlValidator.resolveValidationProfile(sweden));

        // SX-validators only exist in the norway profile
        assertFalse(siriXmlValidator.getValidationRules("norway").getOrDefault(SiriDataType.SITUATION_EXCHANGE, Set.of()).isEmpty());
        assertTrue(siriXmlValidator.getValidationRules("sweden").getOrDefault(SiriDataType.SITUATION_EXCHANGE, Set.of()).isEmpty());

        // Both profiles have ET-validators, but they differ
        assertFalse(siriXmlValidator.getValidationRules("sweden").get(SiriDataType.ESTIMATED_TIMETABLE).isEmpty());
        assertNotEquals(siriXmlValidator.getValidationRules("norway").get(SiriDataType.ESTIMATED_TIMETABLE),
            siriXmlValidator.getValidationRules("sweden").get(SiriDataType.ESTIMATED_TIMETABLE));
    }

    @Test
    public void testUnknownProfileFallsBackToDefault() {
        SubscriptionSetup subscription = new SubscriptionSetup();
        subscription.setValidationProfile("swedan");

        assertFalse(siriXmlValidator.isKnownValidationProfile("swedan"));
        assertTrue(siriXmlValidator.isKnownValidationProfile(" Norway "));
        assertEquals("test", siriXmlValidator.resolveValidationProfile(subscription));
    }
}
