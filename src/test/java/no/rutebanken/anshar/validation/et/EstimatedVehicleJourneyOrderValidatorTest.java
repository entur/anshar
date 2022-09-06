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

package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.OrderValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class EstimatedVehicleJourneyOrderValidatorTest extends CustomValidatorTest {
    static JAXBContext jaxbContext;
    static OrderValidator validator;

    @BeforeAll
    public static void init() {
        validator = new OrderValidator();
        try {
            jaxbContext = JAXBContext.newInstance(Siri.class);
        } catch (JAXBException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testCorrectOrder() {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());
        List<EstimatedCall> estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();

        for (int i = 0; i < 10; i++) {
            EstimatedCall call = new EstimatedCall();
            call.setOrder(BigInteger.valueOf(i+1));
            estimatedCalls.add(call);
        }
        assertNull(validator.isValid(convertToXmlNode(journey)), "Valid Order flagged as invalid");
    }

    @Test
    public void testRecordedCallsAndEstimatedCallsWithOrder() {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();

        journey.setRecordedCalls(new EstimatedVehicleJourney.RecordedCalls());
        journey.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());

        int callCounter = 0;
        List<RecordedCall> recordedCalls = journey.getRecordedCalls().getRecordedCalls();
        for (int i = 0; i < 10; i++) {
            RecordedCall call = new RecordedCall();
            call.setOrder(BigInteger.valueOf(i+1));
            recordedCalls.add(call);
        }
        callCounter = recordedCalls.size();

        List<EstimatedCall> estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();
        for (int i = callCounter; i < callCounter+10; i++) {
            EstimatedCall call = new EstimatedCall();
            call.setOrder(BigInteger.valueOf(i+1));
            estimatedCalls.add(call);
        }

        assertNull(validator.isValid(convertToXmlNode(journey)), "Recorded- and EstimatedCalls with correct Order flagged as valid");
    }

    @Test
    public void testMissingSingleOrder() {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        journey.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());

        List<EstimatedCall> estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                continue;
            }
            EstimatedCall call = new EstimatedCall();
            call.setOrder(BigInteger.valueOf(i+1));
            estimatedCalls.add(call);
        }

        assertNotNull(validator.isValid(convertToXmlNode(journey)), "Single missing Order flagged as valid");
    }

    @Test
    public void testMissingSingleOrderInRecordedCalls() {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();

        journey.setRecordedCalls(new EstimatedVehicleJourney.RecordedCalls());
        journey.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());

        int callCounter = 0;
        List<RecordedCall> recordedCalls = journey.getRecordedCalls().getRecordedCalls();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                continue;
            }
            RecordedCall call = new RecordedCall();
            call.setOrder(BigInteger.valueOf(i+1));
            recordedCalls.add(call);
        }
        callCounter = recordedCalls.size();

        List<EstimatedCall> estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();
        for (int i = callCounter; i < callCounter+10; i++) {
            EstimatedCall call = new EstimatedCall();
            call.setOrder(BigInteger.valueOf(i+1));
            estimatedCalls.add(call);
        }

        assertNotNull(validator.isValid(convertToXmlNode(journey)), "Single missing Order flagged as valid");

    }

    private Node convertToXmlNode(Object o) {

        StringWriter sw = new StringWriter();
        try {
            jaxbContext.createMarshaller().marshal(o, sw);
        } catch (JAXBException e) {
            fail("Error creating XML");
            e.printStackTrace();
        }

        return createXmlNode(sw.toString());
    }

}
