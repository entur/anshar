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

package no.rutebanken.anshar.routes;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelRouteNames {

    public static final String SINGLETON_ROUTE_DEFINITION_GROUP_NAME = "AnsharRutebankenSingletonRoute";


    private static final String QUEUE_PREFIX = "anshar.siri";
    public static final String TRANSFORM_QUEUE = QUEUE_PREFIX + ".transform";
    public static final String DEFAULT_PROCESSOR_QUEUE = QUEUE_PREFIX + ".process";
    public static final String FETCHED_DELIVERY_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".fetched.delivery";

}