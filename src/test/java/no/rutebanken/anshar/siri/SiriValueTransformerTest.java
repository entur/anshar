package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.routes.siri.adapters.NsrValueAdapters;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import no.rutebanken.anshar.subscription.MappingAdapterPresets;
import org.junit.Test;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertNotNull;

public class SiriValueTransformerTest {

    @Test
    public void testForNullPointer() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        Siri siri = null;

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        siri = transformer.transform(siri, mappingAdapters);

        assertNull(siri);
    }

    @Test
    public void testForNullAdapters() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        siri = transformer.transform(siri, null);
        assertEquals("LineRef should not be altered", lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef should not be altered", blockRefValue, getBlockRefFromSiriObj(siri));

        assertNotNull(siri);
    }

    @Test
    public void testLineRefLeftpad() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "0099";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef should not be padded", blockRefValue, getBlockRefFromSiriObj(siri));

    }

    @Test
    public void testMultipleLineRefAdapters() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "123:4";
        String blockRefValue = "";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "012304";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 6, '0'));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef should not be padded", blockRefValue, getBlockRefFromSiriObj(siri));

    }

    @Test
    public void testOutboundMappingAdapters() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

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
        mappingAdapters.addAll(new NsrValueAdapters().createIdPrefixAdapters("TEST"));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef should not be padded", blockRefValue, getBlockRefFromSiriObj(siri));

        Siri mappedIdSiri = transformer.transform(siri, new MappingAdapterPresets().getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT));
        assertEquals("Outbound adapters did not return mapped id", mappedLineRefValue, getLineRefFromSiriObj(mappedIdSiri));

        Siri originalIdSiri = transformer.transform(siri, new MappingAdapterPresets().getOutboundAdapters(OutboundIdMappingPolicy.ORIGINAL_ID));
        assertEquals("Outbound adapters did not return original id", lineRefValue, getLineRefFromSiriObj(originalIdSiri));

        // Create LineRef as expected by OTP
        String otpFriendlyLineRefValue = mappedLineRefValue.replaceAll(":", ".");

        Siri otpFriendlyIdSiri = transformer.transform(siri, new MappingAdapterPresets().getOutboundAdapters(OutboundIdMappingPolicy.OTP_FRIENDLY_ID));
        assertEquals("Outbound adapters did not return OTP-friendly id", otpFriendlyLineRefValue, getLineRefFromSiriObj(otpFriendlyIdSiri));

    }

    @Test
    public void testLongLineRefRuterSubstring() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "9999:123";

        Siri siri = createSiriObject(lineRefValue, null);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        String trimmedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "9999123";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been trimmed as expected", trimmedLineRef, getLineRefFromSiriObj(siri));

    }

    @Test
    public void testShortLineRefRuterSubstring() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "9999:3";

        Siri siri = createSiriObject(lineRefValue, null);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        String trimmedLineRef = lineRefValue + SiriValueTransformer.SEPARATOR + "999903";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been trimmed as expected", trimmedLineRef, getLineRefFromSiriObj(siri));

    }

    @Test
    public void testBlockRefLeftpad() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri));
        String paddedBlockRef = blockRefValue + SiriValueTransformer.SEPARATOR + "0034";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(BlockRefStructure.class, 4, '0'));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("BlockRef has not been padded as expected", paddedBlockRef, getBlockRefFromSiriObj(siri));
        assertEquals("LineRef should not be padded", lineRefValue, getLineRefFromSiriObj(siri));

    }

    @Test
    public void testLineRefAndBlockRefLeftpad() throws JAXBException {
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

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef has not been padded as expected", paddedBlockRef, getBlockRefFromSiriObj(siri));

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

        Siri transformed = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(transformed));
        assertEquals("BlockRef has not been padded as expected", paddedBlockRef, getBlockRefFromSiriObj(transformed));

        assertEquals("Original Lineref has been altered", lineRefValue, getLineRefFromSiriObj(siri));
        assertEquals("Original Blockref has been altered", blockRefValue, getBlockRefFromSiriObj(siri));

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
