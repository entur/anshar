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

import no.rutebanken.anshar.routes.validation.validators.et.RecordedCallRequiredFieldsValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RecordedCallRequiredFieldsValidatorTest extends CustomValidatorTest {

    private static RecordedCallRequiredFieldsValidator validator;

    @BeforeAll
    public static void init() {
        validator = new RecordedCallRequiredFieldsValidator();
    }


    @Test
    public void testRecordedCallBothActualTimesSet() {
        String actualTimesSet =
                        "<RecordedCall>\n" +
                        "    <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                        "    <Order>1</Order>\n" +
                        "    <AimedArrivalTime>2019-02-27T17:48:00+01:00</AimedArrivalTime>\n" +
                        "    <ActualArrivalTime>2019-02-27T17:55:00+01:00</ActualArrivalTime>\n" +
                        "    <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                        "    <ActualDepartureTime>2019-02-27T17:55:00+01:00</ActualDepartureTime>\n" +
                        "</RecordedCall>";
        ValidationEvent valid = validator.isValid(createXmlNode(actualTimesSet));

        assertNull(valid, "Both actual-times set - flagged as invalid");
    }

    @Test
    public void testRecordedCallExpectedArrivalActualDepartureSet() {
        String actualTimesSet =
                        "<RecordedCall>\n" +
                        "    <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                        "    <Order>1</Order>\n" +
                        "    <AimedArrivalTime>2019-02-27T17:48:00+01:00</AimedArrivalTime>\n" +
                        "    <ExpectedArrivalTime>2019-02-27T17:55:00+01:00</ExpectedArrivalTime>\n" +
                        "    <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                        "    <ActualDepartureTime>2019-02-27T17:55:00+01:00</ActualDepartureTime>\n" +
                        "</RecordedCall>";
        ValidationEvent valid = validator.isValid(createXmlNode(actualTimesSet));

        assertNull(valid, "Expected arrival set - flagged as invalid");
    }

    @Test
    public void testRecordedCallActualArrivalExpectedDepartureSet() {
        String actualTimesSet =
                        "<RecordedCall>\n" +
                        "    <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                        "    <Order>1</Order>\n" +
                        "    <AimedArrivalTime>2019-02-27T17:48:00+01:00</AimedArrivalTime>\n" +
                        "    <ActualArrivalTime>2019-02-27T17:55:00+01:00</ActualArrivalTime>\n" +
                        "    <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                        "    <ExpectedDepartureTime>2019-02-27T17:55:00+01:00</ExpectedDepartureTime>\n" +
                        "</RecordedCall>";
        ValidationEvent valid = validator.isValid(createXmlNode(actualTimesSet));

        assertNull(valid, "Expected arrival set - flagged as invalid");
    }



    @Test
    public void testRecordedCallNoUpdatedTimesSet() {
        String actualTimesSet =
                        "<RecordedCall>\n" +
                        "    <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                        "    <Order>1</Order>\n" +
                        "    <AimedArrivalTime>2019-02-27T17:48:00+01:00</AimedArrivalTime>\n" +
                        "    <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                        "</RecordedCall>";
        ValidationEvent valid = validator.isValid(createXmlNode(actualTimesSet));

        assertNotNull(valid, "No actual-times set - flagged as valid");
    }

    @Test
    public void testRecordedCallNoUpdatedArrivalSet() {
        String actualTimesSet =
                        "<RecordedCall>\n" +
                        "    <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                        "    <Order>1</Order>\n" +
                        "    <AimedArrivalTime>2019-02-27T17:48:00+01:00</AimedArrivalTime>\n" +
                        "    <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                        "    <ActualDepartureTime>2019-02-27T17:55:00+01:00</ActualDepartureTime>\n" +
                        "</RecordedCall>";
        ValidationEvent valid = validator.isValid(createXmlNode(actualTimesSet));

        assertNotNull(valid, "Updated arrival not set - flagged as valid");
    }

    @Test
    public void testRecordedCallNoUpdatedDepartureSet() {
        String actualTimesSet =
                        "<RecordedCall>\n" +
                        "    <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                        "    <Order>1</Order>\n" +
                        "    <AimedArrivalTime>2019-02-27T17:48:00+01:00</AimedArrivalTime>\n" +
                        "    <ExpectedArrivalTime>2019-02-27T17:55:00+01:00</ExpectedArrivalTime>\n" +
                        "    <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                        "</RecordedCall>";
        ValidationEvent valid = validator.isValid(createXmlNode(actualTimesSet));

        assertNotNull(valid, "Updated arrival not set - flagged as valid");
    }
}
