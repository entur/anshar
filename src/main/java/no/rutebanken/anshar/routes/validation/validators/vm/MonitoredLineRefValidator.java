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

package no.rutebanken.anshar.routes.validation.validators.vm;

import no.rutebanken.anshar.routes.validation.validators.NsrGenericIdValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;

import static no.rutebanken.anshar.routes.validation.validators.Constants.MONITORED_VEHICLE_JOURNEY;


/**
 * Verifies that the value for field LineRef is built up correctly
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class MonitoredLineRefValidator extends NsrGenericIdValidator {


    private final String path;

    public MonitoredLineRefValidator() {
        FIELDNAME = "LineRef";
        ID_PATTERN = "Line";
        path = MONITORED_VEHICLE_JOURNEY + "/" + FIELDNAME;
    }

    @Override
    public String getXpath() {
        return path;
    }
}
