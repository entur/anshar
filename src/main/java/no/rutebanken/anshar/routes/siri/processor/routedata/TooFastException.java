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
package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri21.EstimatedVehicleJourney;

import java.time.ZonedDateTime;

import static no.rutebanken.anshar.util.SiriUtils.resolveServiceJourneyId;

public class TooFastException extends Throwable {

    private final String msg;

    public TooFastException(EstimatedVehicleJourney serviceJourneyId, String fromStop, String toStop, ZonedDateTime fromTime, ZonedDateTime toTime) {

        double distance = StopsUtil.getDistance(fromStop, toStop);

        long seconds = StopsUtil.getSeconds(fromTime, toTime);
        int kph = StopsUtil.calculateSpeedKph(distance, fromTime, toTime);

        this.msg = "Too fast (" + kph + " kph) between " + fromStop + " and " + toStop +" (" + Math.round(distance) + " meters in " + seconds + "s) [" + resolveServiceJourneyId(serviceJourneyId) + "].";
    }


    @Override
        public String getMessage() {
            return msg;
        }
    }