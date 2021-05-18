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

import no.rutebanken.anshar.routes.validation.validators.et.IncreasingTimesValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IncreasingTimesValidatorTest extends CustomValidatorTest {

    IncreasingTimesValidator validator = new IncreasingTimesValidator();


    @Test
    public void testIncreasingEstimatedCalls() {

        ValidationEvent valid = validator.isValid(createXmlNode(increasingEstimatedCalls));

        assertNull(valid, "Valid, increasing times - all EstimatedCalls - flagged as invalid");

    }

    @Test
    public void testIncreasingRecordedEstimatedCalls() {
        ValidationEvent valid = validator.isValid(createXmlNode(increasingRecordedEstimatedCalls));

        assertNull(valid, "Valid, increasing times - Recorded- and EstimatedCalls - flagged as invalid");
    }


    @Test
    public void testNonIncreasingRecordedEstimatedCalls() {

        ValidationEvent valid = validator.isValid(createXmlNode(nonIncreasingRecordedEstimatedCalls));

        assertNotNull(valid, "Invalid, non-increasing times - Recorded- and EstimatedCalls - flagged as valid");
    }


    @Test
    public void testNegativeDwellTimes() {

        ValidationEvent valid = validator.isValid(createXmlNode(negativeDwellTimeEstimatedCalls));

        assertNotNull(valid, "Negative dwell-times flagged as valid");
    }

    @Test
    public void testNegativeDwellTimesWithCancellation() {

        ValidationEvent valid = validator.isValid(createXmlNode(negativeDwellTimeWithCancellationEstimatedCalls));

        assertNull(valid, "Negative dwell-times flagged as invalid even though departure is cancelled");
    }


    private static final String increasingEstimatedCalls =
            "<EstimatedVehicleJourney>\n" +
            "    <LineRef>NSB:Line:-</LineRef>\n" +
            "    <DirectionRef>Kristiansand</DirectionRef>\n" +
            "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
            "    <VehicleMode>rail</VehicleMode>\n" +
            "    <OperatorRef>NSB</OperatorRef>\n" +
            "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
            "    <DataSource>BNR</DataSource>\n" +
            "    <VehicleRef>734</VehicleRef>\n" +
            "    <EstimatedCalls>\n" +
            "        <EstimatedCall>\n" +
            "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
            "            <Order>1</Order>\n" +
            "            <StopPointName>Stavanger</StopPointName>\n" +
            "            <RequestStop>false</RequestStop>\n" +
            "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
            "            <ExpectedDepartureTime>2019-02-27T17:48:00+01:00</ExpectedDepartureTime>\n" +
            "            <DepartureStatus>onTime</DepartureStatus>\n" +
            "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
            "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
            "        </EstimatedCall>\n" +
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


    private static final String nonIncreasingRecordedEstimatedCalls =
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
                    "            <ActualDepartureTime>2019-02-27T17:55:00+01:00</ActualDepartureTime>\n" +   //    <<== Departure before arrival next stop
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
                    "            <ExpectedArrivalTime>2019-02-27T17:54:01+01:00</ExpectedArrivalTime>\n" +     //    <<== Departure from last stop before arrival
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


    private static final String negativeDwellTimeEstimatedCalls =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-27T17:48:00+01:00</ExpectedDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T17:54:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:55:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-27T17:53:00+01:00</ExpectedDepartureTime>\n" +      //    <<== Departure before arrival
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
                    "            <ArrivalBoardingActivity>alighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";


    private static final String negativeDwellTimeWithCancellationEstimatedCalls =
            "<EstimatedVehicleJourney>\n" +
                    "    <LineRef>NSB:Line:-</LineRef>\n" +
                    "    <DirectionRef>Kristiansand</DirectionRef>\n" +
                    "    <DatedVehicleJourneyRef>734:2019-02-27</DatedVehicleJourneyRef>\n" +
                    "    <VehicleMode>rail</VehicleMode>\n" +
                    "    <OperatorRef>NSB</OperatorRef>\n" +
                    "    <ServiceFeatureRef>passengerTrain</ServiceFeatureRef>\n" +
                    "    <DataSource>BNR</DataSource>\n" +
                    "    <VehicleRef>734</VehicleRef>\n" +
                    "    <EstimatedCalls>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:968</StopPointRef>\n" +
                    "            <Order>1</Order>\n" +
                    "            <StopPointName>Stavanger</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:48:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-27T17:48:00+01:00</ExpectedDepartureTime>\n" +
                    "            <DepartureStatus>onTime</DepartureStatus>\n" +
                    "            <DeparturePlatformName>1</DeparturePlatformName>\n" +
                    "            <DepartureBoardingActivity>boarding</DepartureBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "        <EstimatedCall>\n" +
                    "            <StopPointRef>NSR:Quay:609</StopPointRef>\n" +
                    "            <Order>2</Order>\n" +
                    "            <StopPointName>Jåttåvågen</StopPointName>\n" +
                    "            <RequestStop>false</RequestStop>\n" +
                    "            <AimedArrivalTime>2019-02-27T17:54:00+01:00</AimedArrivalTime>\n" +
                    "            <ExpectedArrivalTime>2019-02-27T17:54:00+01:00</ExpectedArrivalTime>\n" +
                    "            <ArrivalStatus>onTime</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>2</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "            <AimedDepartureTime>2019-02-27T17:55:00+01:00</AimedDepartureTime>\n" +
                    "            <ExpectedDepartureTime>2019-02-27T17:53:00+01:00</ExpectedDepartureTime>\n" +      //    <<== Departure before arrival
                    "            <DepartureStatus>cancelled</DepartureStatus>\n" +                                  //    <<== ...but departure is cancelled
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
                    "            <ArrivalStatus>cancelled</ArrivalStatus>\n" +
                    "            <ArrivalPlatformName>6</ArrivalPlatformName>\n" +
                    "            <ArrivalBoardingActivity>noAlighting</ArrivalBoardingActivity>\n" +
                    "        </EstimatedCall>\n" +
                    "    </EstimatedCalls>\n" +
                    "    <IsCompleteStopSequence>true</IsCompleteStopSequence>\n" +
                    "</EstimatedVehicleJourney>";

}
