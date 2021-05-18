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

import no.rutebanken.anshar.routes.validation.validators.et.SaneDelayValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SaneDelayValidatorTest extends CustomValidatorTest {

    private static SaneDelayValidator validator;

    @BeforeAll
    public static void init() {
        validator = new SaneDelayValidator();
    }

    @Test
    public void testSaneDelay() throws Exception {

        ValidationEvent valid = validator.isValid(createXmlNode(increasingRecordedEstimatedCalls));

        assertNull(valid, "Valid, sane delays flagged as invalid");
    }

    @Test
    public void testTooLongDelayRecordedCalls() throws Exception {

        ValidationEvent valid = validator.isValid(createXmlNode(tooLongDelayRecordedCalls));

        assertNotNull(valid, "Too long delay flagged as sane");
    }


    @Test
    public void testTooLongDelayEstimatedCalls() throws Exception {

        ValidationEvent valid = validator.isValid(createXmlNode(tooLongDelayRecordedCalls));

        assertNotNull(valid, "Too long delay flagged as sane");
    }


    @Test
    public void testMissingActualTimesInRecordedCalls() throws Exception {

        ValidationEvent validArrival = validator.isValid(createXmlNode(increasingRecordedCallsMissingActualArrival));

        assertNull(validArrival, "Missing values flagged as too long");

        ValidationEvent validDeparture = validator.isValid(createXmlNode(increasingRecordedCallsMissingActualDeparture));

        assertNull(validDeparture, "Missing values flagged as too long");
    }


    private static final String increasingRecordedEstimatedCalls =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <RecordedCalls>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ActualDepartureTime>2019-02-27T17:48:00+01:00</ActualDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "    </RecordedCalls>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:01+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T17:54:01+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:55:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-27T17:55:00+01:00</ExpectedDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>2</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:1146</StopPointRef>\n" +
                    "            <Order>3</Order>\n" +
                    "            <StopPointName>Sandnes Sentrum</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T18:01:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T18:01:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>6</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";


    private static final String increasingRecordedCallsMissingActualDeparture =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <RecordedCalls>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ActualDepartureTime>2019-02-27T17:48:00+01:00</ActualDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:01+01:00</AimedArrivalTime>\n" +
                    "            <ActualArrivalTime>2019-02-27T17:54:01+01:00</ActualArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:55:00+01:00</AimedDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>2</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "    </RecordedCalls>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:1146</StopPointRef>\n" +
                    "            <Order>3</Order>\n" +
                    "            <StopPointName>Sandnes Sentrum</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T18:01:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T18:01:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>6</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";

    private static final String increasingRecordedCallsMissingActualArrival =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <RecordedCalls>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ActualDepartureTime>2019-02-27T17:48:00+01:00</ActualDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:01+01:00</AimedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:54:01+01:00</AimedDepartureTime>\n" +
                    "            <ActualDepartureTime>2019-02-27T17:55:00+01:00</ActualDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>2</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "    </RecordedCalls>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:1146</StopPointRef>\n" +
                    "            <Order>3</Order>\n" +
                    "            <StopPointName>Sandnes Sentrum</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T18:01:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T18:01:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>6</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";


    private static final String tooLongDelayRecordedCalls =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <RecordedCalls>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ActualDepartureTime>2019-02-28T17:48:01+01:00</ActualDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "    </RecordedCalls>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:01+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-28T17:54:01+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:55:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-28T17:55:00+01:00</ExpectedDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>2</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:1146</StopPointRef>\n" +
                    "            <Order>3</Order>\n" +
                    "            <StopPointName>Sandnes Sentrum</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T18:01:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-28T18:01:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>6</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";

    private static final String tooLongDelayEstimatedCalls =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <RecordedCalls>\n" +
                    "        <RecordedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ActualDepartureTime>2019-02-27T17:48:00+01:00</ActualDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </RecordedCall>\n" +
                    "    </RecordedCalls>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:01+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T17:54:01+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:55:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-28T17:55:01+01:00</ExpectedDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>2</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:1146</StopPointRef>\n" +
                    "            <Order>3</Order>\n" +
                    "            <StopPointName>Sandnes Sentrum</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T18:01:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T18:01:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>6</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";

}
