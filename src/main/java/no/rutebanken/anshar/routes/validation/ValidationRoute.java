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

package no.rutebanken.anshar.routes.validation;

import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@Service
@Configuration
public class ValidationRoute extends RestRouteBuilder {

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    private SubscriptionManager subscriptionManager;


    @Override
    public void configure() throws Exception {
        super.configure();
        rest("/anshar/validation")
                .tag("validation")
                .get("/{" + PARAM_CODESPACE + "}").produces(MediaType.TEXT_HTML).to("direct:validation.list")
                .get("/report").produces(MediaType.TEXT_HTML).to("direct:validation.report")
                .put("/toggle").produces(MediaType.TEXT_HTML).to("direct:validation.toggle")
                .get("/siri").produces(MediaType.APPLICATION_XML).to("direct:validation.siri")
        ;

        from("direct:validation.toggle")
                .filter(header(PARAM_SUBSCRIPTION_ID).isNotNull())
                .process(p -> toggleValidation(p.getIn().getHeader(PARAM_SUBSCRIPTION_ID, Long.class), (String) p.getIn().getHeader("filter")))
                .routeId("admin.validation.toggle")
        ;

        from("direct:validation.list")
                .bean(subscriptionManager, "getSubscriptionsForCodespace(${header."+ PARAM_CODESPACE+"})")
                .to("direct:removeHeaders")
                .to("freemarker:templates/validation.ftl")
                .routeId("admin.validation.list")
        ;
        from("direct:validation.report")
                .bean(siriXmlValidator, "getValidationResults(${header." + PARAM_SUBSCRIPTION_ID + "})")
                .to("direct:removeHeaders")
                .to("freemarker:templates/validation-report.ftl")
                .routeId("admin.validation.report")
        ;

        from("direct:validation.siri")
                .bean(siriXmlValidator, "getValidatedSiri(${header." + PARAM_VALIDATION_REF + "})")
                .to("direct:removeHeaders")
                .setHeader("Content-Disposition", simple("attachment; filename=\"SIRI.xml\""))
                .routeId("admin.validation.siri")
        ;

    }

    private void toggleValidation(Long subscriptionId, String validationFilter) {
        log.info("got validationFilter: " + validationFilter);
        SubscriptionSetup subscriptionSetup = subscriptionManager.getSubscriptionById(subscriptionId);
        if (subscriptionSetup != null) {
            subscriptionSetup.setValidation(! subscriptionSetup.isValidation());
            if (subscriptionSetup.isValidation()) {
                //Validation has now been switched on - clear previous results
                siriXmlValidator.clearValidationResults(subscriptionSetup.getSubscriptionId());
                siriXmlValidator.addFilter(subscriptionSetup.getSubscriptionId(), validationFilter);
                subscriptionSetup.setValidationFilter(validationFilter);
            }
            log.info("Toggling validation, validation is now {}", (subscriptionSetup.isValidation()?"active":"disabled"));
            subscriptionManager.updateSubscription(subscriptionSetup);
        }
    }

}
