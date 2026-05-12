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
package no.rutebanken.anshar.routes.admin.auth;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class BasicAuthService implements Processor {

   @Value("${anshar.login.username:}")
   String username;

   @Value("${anshar.login.password:}")
   String password;

   public void process(Exchange exchange) throws Exception {
      if (!exchange.getIn().getHeaders().containsKey("Authorization")) {
         throw new AccessDeniedException("username/password required");
      }
      try {
         String authorization = exchange.getIn().getHeader("Authorization", String.class);
         String userpass = new String(Base64.decodeBase64(authorization.replaceAll("Basic ", "")));

         String[] tokens = userpass.split(":");

         // create an Authentication object
         UsernamePasswordAuthenticationToken authToken =
                 new UsernamePasswordAuthenticationToken(tokens[0], tokens[1]);

         if (!(authToken.getName().equals(username) & authToken.getCredentials().equals(password))) {
            throw new AccessDeniedException("username/password does not match");
         }

      } catch (Throwable t) {
         throw new AccessDeniedException("username/password does not match");
      }
   }
}