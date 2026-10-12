package no.rutebanken.anshar.routes.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Only accepts requests made directly from the local host (e.g. the preStop-hook of the pod).
 * Requests routed through an ingress/proxy are rejected.
 */
@Component
public class LocalRequestOnlyService implements Processor {

   public void process(Exchange exchange) throws Exception {
      HttpServletRequest request = exchange.getIn().getBody(HttpServletRequest.class);
      if (request == null || !isLocalRequest(request)) {
         throw new AccessDeniedException("only available from localhost");
      }
   }

   static boolean isLocalRequest(HttpServletRequest request) {
      if (request.getHeader("X-Forwarded-For") != null || request.getHeader("Forwarded") != null) {
         return false;
      }
      try {
         return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
      } catch (Exception e) {
         return false;
      }
   }
}
