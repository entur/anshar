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

package no.rutebanken.anshar.routes.siri.helpers;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri21.Siri;

public class SiriRequestFactory {

	private final SubscriptionSetup subscriptionSetup;

	public static String getCamelUrl(String url) {
		return getCamelUrl(url, null);
	}
	public static String getCamelUrl(String url, String parameters) {
		if (url != null) {
			if (parameters != null && !parameters.isEmpty()) {
				String separator = "?";
				if (url.contains("?")) {
					separator = "&";
				}
				if (parameters.startsWith("?")) {
					parameters = parameters.substring(1);
				}
				url = url + separator + parameters;
			}
		}
		return url;
	}

	public SiriRequestFactory(SubscriptionSetup subscriptionSetup) {
		this.subscriptionSetup = subscriptionSetup;
	}

	/*
	 * Called dynamically from camel-routes
	 */
	public Siri createSiriSubscriptionRequest() {
		return SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
	}

	/*
	 * Called dynamically from camel-routes
	 */
	public Siri createSiriTerminateSubscriptionRequest() {
		return SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

	}

	/*
	 * Called dynamically from camel-routes
	 */
	public Siri createSiriCheckStatusRequest() {
		return SiriObjectFactory.createCheckStatusRequest(subscriptionSetup);

	}

	private Boolean allData = Boolean.TRUE;

	/*
	 * Called dynamically from camel-routes
	 *
	 * Creates ServiceRequest or DataSupplyRequest based on subscription type
	 */
	public Siri createSiriDataRequest() {
		Siri request;
		if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {
			request = SiriObjectFactory.createServiceRequest(subscriptionSetup);
		} else {
			request = SiriObjectFactory.createDataSupplyRequest(subscriptionSetup, allData);
			allData = Boolean.FALSE;
		}

		return request;
	}

}
