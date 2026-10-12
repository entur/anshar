package no.rutebanken.anshar.routes.admin.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalRequestOnlyServiceTest {

    @Test
    public void testLoopbackRequestIsLocal() {
        assertTrue(LocalRequestOnlyService.isLocalRequest(request("127.0.0.1")));
        assertTrue(LocalRequestOnlyService.isLocalRequest(request("0:0:0:0:0:0:0:1")));
    }

    @Test
    public void testRemoteRequestIsNotLocal() {
        assertFalse(LocalRequestOnlyService.isLocalRequest(request("10.0.0.12")));
    }

    @Test
    public void testForwardedRequestIsNotLocal() {
        // E.g. forwarded by a sidecar/proxy running in the same pod
        MockHttpServletRequest forwarded = request("127.0.0.1");
        forwarded.addHeader("X-Forwarded-For", "203.0.113.10");
        assertFalse(LocalRequestOnlyService.isLocalRequest(forwarded));
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
