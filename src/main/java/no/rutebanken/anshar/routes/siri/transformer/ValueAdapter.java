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

package no.rutebanken.anshar.routes.siri.transformer;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;

import java.io.Serializable;

public abstract class ValueAdapter implements Serializable {

    private Class clazz;

    private transient PrometheusMetricsService metricsService;

    protected ValueAdapter(Class clazz) {
        this.clazz = clazz;
    }

    protected ValueAdapter() {
        // Empty constructor
    }

    protected PrometheusMetricsService getMetricsService() {
        if (metricsService == null) {
            metricsService = ApplicationContextHolder.getContext().getBean(PrometheusMetricsService.class);
        }
        return metricsService;
    }

    public Class getClassToApply() {
        return clazz;
    }

    protected abstract String apply(String value);

    public String toString() {
        return this.getClass().getSimpleName() + "[" + (clazz != null ? clazz.getSimpleName():"null") + "]";
    }
}
