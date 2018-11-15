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

package no.rutebanken.anshar.data;

import no.rutebanken.anshar.subscription.SiriDataType;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RequestorRefStats implements Serializable {

    final String requestorRef;
    public final String clientName;
    public final String datasetId;
    final SiriDataType dataType;
    public List<String> lastRequests;

    private static transient final int maxListSize = 5;
    private static transient final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    protected RequestorRefStats(String requestorRef, String datasetId, String clientName, SiriDataType dataType) {
        this.requestorRef = requestorRef;
        this.clientName = clientName;
        this.datasetId = datasetId;
        this.dataType = dataType;
        this.lastRequests = new ArrayList<>();
    }

    protected void touch(Instant time) {
        lastRequests.add(0, formatter.format(time));
        if(lastRequests.size() > maxListSize) {
            //remove last element
            lastRequests.remove(lastRequests.size()-1);
        }
    }
}
