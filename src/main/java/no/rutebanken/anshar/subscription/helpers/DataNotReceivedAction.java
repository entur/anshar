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
package no.rutebanken.anshar.subscription.helpers;

import java.io.Serializable;

public class DataNotReceivedAction implements Serializable {

    private int inactivityMinutes;
    private boolean enabled;
    private String jsonPostContent;
    private String endpoint;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getInactivityMinutes() {
        return inactivityMinutes;
    }

    public void setInactivityMinutes(int inactivityMinutes) {
        this.inactivityMinutes = inactivityMinutes;
    }

    public String getJsonPostContent() {
        return jsonPostContent;
    }

    public void setJsonPostContent(String jsonPostContent) {
        this.jsonPostContent = jsonPostContent;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
