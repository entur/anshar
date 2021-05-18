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

package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.adapters.NsrValueAdapters;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.BlockRefStructure;
import uk.org.siri.siri20.DestinationRef;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.JourneyPlaceRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.ServiceDelivery;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SiriValueTransformerTest extends SpringBootBaseTest {

    @Test
    public void testForNullPointer() throws JAXBException {

        Siri siri = null;

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertNull(siri);
    }

    @Test
    public void testForNullAdapters() throws JAXBException {
        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        siri = SiriValueTransformer.transform(siri, null);
        assertEquals(lineRefValue, getLineRefFromSiriObj(siri), "LineRef should not be altered");
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri), "BlockRef should not be altered");

        assertNotNull(siri);
    }

    @Test
    public void testLineRefLeftpad() throws JAXBException {
        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "0099";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(paddedLineRef, getLineRefFromSiriObj(siri), "LineRef has not been padded as expected");
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri), "BlockRef should not be padded");

    }

    @Test
    public void testMultipleLineRefAdapters() throws JAXBException {
        String lineRefValue = "123:4";
        String blockRefValue = "";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "012304";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 6, '0'));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(paddedLineRef, getLineRefFromSiriObj(siri), "LineRef has not been padded as expected");
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri), "BlockRef should not be padded");

    }

    @Test
    public void testOutboundMappingAdapters() throws JAXBException {
        String lineRefValue = "123:4";
        String blockRefValue = "";
        String mappedLineRefValue = "TEST:Line:012304";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + mappedLineRefValue;

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 6, '0'));
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setDatasetId("TEST");
        subscriptionSetup.setSubscriptionType(SiriDataType.ESTIMATED_TIMETABLE);

        mappingAdapters.addAll(new NsrValueAdapters().createIdPrefixAdapters(subscriptionSetup));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(paddedLineRef, getLineRefFromSiriObj(siri), "LineRef has not been padded as expected");
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri), "BlockRef should not be padded");

        Siri mappedIdSiri = SiriValueTransformer.transform(siri, MappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT));
        assertEquals(mappedLineRefValue, getLineRefFromSiriObj(mappedIdSiri), "Outbound adapters did not return mapped id");

        Siri originalIdSiri = SiriValueTransformer.transform(siri, MappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.ORIGINAL_ID));
        assertEquals(lineRefValue, getLineRefFromSiriObj(originalIdSiri), "Outbound adapters did not return original id");

    }

    @Test
    public void testLongLineRefRuterSubstring() throws JAXBException {
        String lineRefValue = "9999:123";

        Siri siri = createSiriObject(lineRefValue, null);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        String trimmedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "9999123";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(trimmedLineRef, getLineRefFromSiriObj(siri), "LineRef has not been trimmed as expected");

    }

    @Test
    public void testShortLineRefRuterSubstring() throws JAXBException {
        String lineRefValue = "9999:3";

        Siri siri = createSiriObject(lineRefValue, null);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        String trimmedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "999903";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(trimmedLineRef, getLineRefFromSiriObj(siri), "LineRef has not been trimmed as expected");

    }

    @Test
    public void testBlockRefLeftpad() throws JAXBException {
        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedBlockRef = blockRefValue + SiriValueTransformer.SEPARATOR + "0034";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(BlockRefStructure.class, 4, '0'));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(paddedBlockRef, getBlockRefFromSiriObj(siri), "BlockRef has not been padded as expected");
        assertEquals(lineRefValue, getLineRefFromSiriObj(siri), "LineRef should not be padded");

    }

    @Test
    public void testLineRefAndBlockRefLeftpad() throws JAXBException {
        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));

        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "0099";
        String paddedBlockRef = blockRefValue + SiriValueTransformer.SEPARATOR + "0034";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(BlockRefStructure.class, 4, '0'));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        siri = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(paddedLineRef, getLineRefFromSiriObj(siri), "LineRef has not been padded as expected");
        assertEquals(paddedBlockRef, getBlockRefFromSiriObj(siri), "BlockRef has not been padded as expected");

    }

    @Test
    public void testImmutability() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));

        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "0099";
        String paddedBlockRef = blockRefValue + SiriValueTransformer.SEPARATOR + "0034";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(BlockRefStructure.class, 4, '0'));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        Siri transformed = SiriValueTransformer.transform(siri, mappingAdapters);

        assertEquals(paddedLineRef, getLineRefFromSiriObj(transformed), "LineRef has not been padded as expected");
        assertEquals(paddedBlockRef, getBlockRefFromSiriObj(transformed), "BlockRef has not been padded as expected");

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri), "Original Lineref has been altered");
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri), "Original Blockref has been altered");

    }

    private Siri createSiriObject(String lineRefValue, String blockRefValue) {
        return createSiriObject(lineRefValue, blockRefValue, null, null);
    }


    private Siri createSiriObject(String lineRefValue, String blockRefValue, String originStopPointValue, String destinationStopPointValue) {
        Siri siri = new Siri();
            ServiceDelivery serviceDelivery = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure estimatedTimetableDelivery = new EstimatedTimetableDeliveryStructure();
        EstimatedVersionFrameStructure estimatedJourneyVersionFrame = new EstimatedVersionFrameStructure();
        EstimatedVehicleJourney estimatedVehicleJourney = new EstimatedVehicleJourney();

        if (lineRefValue != null) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(lineRefValue);
            estimatedVehicleJourney.setLineRef(lineRef);
        }

        if (blockRefValue != null) {
            BlockRefStructure blockRef = new BlockRefStructure();
            blockRef.setValue(blockRefValue);
            estimatedVehicleJourney.setBlockRef(blockRef);
        }

        if (originStopPointValue != null) {
            JourneyPlaceRefStructure origin = new JourneyPlaceRefStructure();
            origin.setValue(originStopPointValue);
            estimatedVehicleJourney.setOriginRef(origin);
        }

        if (destinationStopPointValue != null) {
            DestinationRef destination = new DestinationRef();
            destination.setValue(destinationStopPointValue);
            estimatedVehicleJourney.setDestinationRef(destination);
        }


        estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().add(estimatedVehicleJourney);
        estimatedTimetableDelivery.getEstimatedJourneyVersionFrames().add(estimatedJourneyVersionFrame);
        serviceDelivery.getEstimatedTimetableDeliveries().add(estimatedTimetableDelivery);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }


    private String getBlockRefFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getBlockRef().getValue();
    }

    private String getLineRefFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getLineRef().getValue();
    }

    private String getOriginFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getOriginRef().getValue();
    }

    private String getDestinationfFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getDestinationRef().getValue();
    }


    private static String readFile(String path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path, "rw");
        byte[] contents = new byte[(int)raf.length()];
        raf.readFully(contents);
        return new String(contents);
    }
}
