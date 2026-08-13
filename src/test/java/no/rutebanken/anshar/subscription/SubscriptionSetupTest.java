/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.transformer.impl.PrefixAdapter;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.LineRef;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscriptionSetupTest {

    private SubscriptionSetup setup_1;
    private SubscriptionSetup setup_2;

    @BeforeEach
    public void setUp() {
        HashMap<RequestType, String> urlMap_1 = new HashMap<>();
        urlMap_1.put(RequestType.SUBSCRIBE, "http://localhost:1234/subscribe");

        HashMap<RequestType, String> urlMap_2 = new HashMap<>();
        urlMap_2.putAll(urlMap_1);

        setup_1 = new SubscriptionSetup(
                SiriDataType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                Duration.ofHours(1),
                Duration.ofHours(1),
                "http://www.kolumbus.no/siri",
                urlMap_1,
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds((long) 1000),
                true
        );

        setup_2 = new SubscriptionSetup(
                SiriDataType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                Duration.ofHours(1),
                Duration.ofHours(1),
                "http://www.kolumbus.no/siri",
                urlMap_2,
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds((long) 1000),
                true
        );
    }

    @Test
    public void testSimpleEquals() {
        assertEquals(setup_1, setup_2);
    }

    @Test
    public void testEqualsUpdatedSubscriptionType() {
        assertEquals(setup_1, setup_2);
        setup_2.setSubscriptionType(SiriDataType.VEHICLE_MONITORING);
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedAddress() {
        assertEquals(setup_1, setup_2);
        setup_2.setAddress("http://other.address");
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedNameSpace() {
        assertEquals(setup_1, setup_2);
        setup_2.setOperatorNamespace("http://other.operator.namespace");
        assertFalse(setup_1.equals(setup_2));
    }

    @Test
    public void testEqualsUpdatedInitialDuration() {
        assertEquals(setup_1, setup_2);
        setup_2.setDurationOfSubscriptionHours((int) (setup_1.getDurationOfSubscription().getSeconds()*2));
        assertFalse(setup_1.equals(setup_2));
    }

//    @Test
//    public void testEqualsUpdatedUrl() {
//        assertEquals(setup_1, setup_2);
//        Map<RequestType, String> urlMap = setup_2.getUrlMap();
//        assertTrue("urlMap does not contain expected URL", urlMap.containsKey(RequestType.SUBSCRIBE));
//        urlMap.put(RequestType.SUBSCRIBE, urlMap.get(RequestType.SUBSCRIBE) + "/updated");
//        assertFalse(setup_1.equals(setup_2));
//    }
//
//    @Test
//    public void testEqualsAddedUrl() {
//        assertEquals(setup_1, setup_2);
//        Map<RequestType, String> urlMap = setup_2.getUrlMap();
//        urlMap.put(RequestType.GET_VEHICLE_MONITORING, urlMap.get(RequestType.SUBSCRIBE) + "/vm");
//        assertFalse(setup_1.equals(setup_2));
//    }

    @Test
    public void testEqualsAlteredSubscriptionIdIgnored() {
        assertFalse(setup_1.getSubscriptionId().equals(setup_2.getSubscriptionId()));
        assertEquals(setup_1, setup_2);
    }

    /**
     * Subscriptions are replicated between pods as serialized objects. A changed serialVersionUID
     * makes pods running different versions unable to read each other's subscriptions during a
     * rolling upgrade - the value must stay pinned even when fields are added or removed.
     */
    @Test
    public void testSerialVersionUidIsPinned() {
        assertEquals(-3520903682492551223L, ObjectStreamClass.lookup(SubscriptionSetup.class).getSerialVersionUID());
    }

    /**
     * mappingAdapters is transient - and transient fields are NOT initialized when deserializing.
     */
    @Test
    public void testMappingAdaptersAreUsableAfterDeserializing() throws Exception {
        setup_1.getMappingAdapters().add(new PrefixAdapter(SiriDataType.SITUATION_EXCHANGE, "tst", LineRef.class, "tst:Line:"));

        SubscriptionSetup deserialized = serializeAndDeserialize(setup_1);

        assertNotNull(deserialized.getMappingAdapters());
        assertTrue(deserialized.getMappingAdapters().isEmpty(), "Adapters should not be replicated between pods");
    }

    private SubscriptionSetup serializeAndDeserialize(SubscriptionSetup setup) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(setup);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (SubscriptionSetup) in.readObject();
        }
    }

}
