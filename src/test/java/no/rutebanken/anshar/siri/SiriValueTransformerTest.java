package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import org.junit.Test;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertEquals;
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
        String paddedLineRef = "0099";

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
        String paddedLineRef = "012304";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0'));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 6, '0'));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef should not be padded", blockRefValue, getBlockRefFromSiriObj(siri));

    }

    @Test
    public void testLineRefRuterSubstring() throws JAXBException {
        SiriValueTransformer transformer = new SiriValueTransformer();

        String lineRefValue = "9999:123";

        Siri siri = createSiriObject(lineRefValue, null);

        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));
        String trimmedLineRef = "99990123";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0'));

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
        String paddedBlockRef = "0034";

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

        String paddedLineRef = "0099";
        String paddedBlockRef = "0034";

        List<ValueAdapter> mappingAdapters = new ArrayList<>();
        mappingAdapters.add(new LeftPaddingAdapter(BlockRefStructure.class, 4, '0'));
        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        siri = transformer.transform(siri, mappingAdapters);

        assertEquals("LineRef has not been padded as expected", paddedLineRef, getLineRefFromSiriObj(siri));
        assertEquals("BlockRef has not been padded as expected", paddedBlockRef, getBlockRefFromSiriObj(siri));

    }

    private Siri createSiriObject(String lineRefValue, String blockRefValue) {
        Siri siri = new Siri();
            ServiceDelivery serviceDelivery = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure estimatedTimetableDelivery = new EstimatedTimetableDeliveryStructure();
        EstimatedVersionFrameStructure estimatedJourneyVersionFrame = new EstimatedVersionFrameStructure();
        EstimatedVehicleJourney estimatedVehicleJourney = new EstimatedVehicleJourney();

            LineRef lineRef = new LineRef();
            lineRef.setValue(lineRefValue);
            estimatedVehicleJourney.setLineRef(lineRef);

            BlockRefStructure blockRef = new BlockRefStructure();
            blockRef.setValue(blockRefValue);
            estimatedVehicleJourney.setBlockRef(blockRef);

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


    private static String readFile(String path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path, "rw");
        byte[] contents = new byte[(int)raf.length()];
        raf.readFully(contents);
        return new String(contents);
    }
}
