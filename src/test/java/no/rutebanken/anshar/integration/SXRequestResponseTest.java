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

package no.rutebanken.anshar.integration;

import io.restassured.http.ContentType;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationNumber;

import java.time.ZonedDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class SXRequestResponseTest extends BaseHttpTest {

    @Autowired
    private
    Situations repo;

    private final String participantRef = "SX";
    private final String situationNumber = "TTT:SituationNumber:1234";

    @BeforeEach
    public void addData() {
        super.init();
        repo.clearAll();
        repo.add(dataSource, createPtSituationElement(participantRef, situationNumber, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1)));
    }

    @Test
    public void testSXRequest() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.SITUATION_EXCHANGE));
        given()
                .when()
                    .contentType(ContentType.XML)
                    .body(SiriXml.toXml(siriRequest))
                    .post("anshar/services")
                .then()
                    .statusCode(200)
                    .rootPath("Siri.ServiceDelivery.SituationExchangeDelivery.Situations.PtSituationElement")
                        .body("SituationNumber", equalTo(situationNumber))
                        .body("ParticipantRef", equalTo(participantRef))
        ;
    }

    @Test
    public void testLiteSXRequest() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                .get("anshar/rest/sx")
                .then()
                .statusCode(200)
                .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.SituationExchangeDelivery.Situations.PtSituationElement")
                    .body("SituationNumber", equalTo(situationNumber))
                    .body("ParticipantRef", equalTo(participantRef))
        ;
    }

    private PtSituationElement createPtSituationElement(String participantRef, String situationNumber, ZonedDateTime startTime, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(startTime);

        element.setParticipantRef(SiriObjectFactory.createRequestorRef(participantRef));

        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);

        //ValidityPeriod has already expired
        period.setEndTime(endTime);
        element.getValidityPeriods().add(period);
        return element;
    }
}
