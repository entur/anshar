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
