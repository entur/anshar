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

import org.apache.camel.Message;

import java.util.Arrays;
import java.util.List;

public class HttpParameter {
    public static final String PARAM_SUBSCRIPTION_ID = "subscriptionId";
    public static final String PARAM_CODESPACE       = "codespace";
    public static final String PARAM_VALIDATION_REF  = "validationRef";
    public static final String PARAM_DATASET_ID      = "datasetId";
    public static final String PARAM_EXCLUDED_DATASET_ID      = "excludedDatasetIds";
    public static final String PARAM_MAX_SIZE        = "maxSize";
    public static final String PARAM_USE_ORIGINAL_ID = "useOriginalId";
    public static final String PARAM_LINE_REF        = "lineRef";
    public static final String PARAM_PREVIEW_INTERVAL   = "previewIntervalMinutes";
    public static final String PARAM_RESPONSE_CODE   = "CamelHttpResponseCode";
    public static final String PARAM_PATH           = "CamelHttpPath";

    public static List<String> getParameterValuesAsList(Message msg, String headerName) {
        String excludedDatasetIds = msg.getHeader(headerName, String.class);

        List<String> excludedIdList = null;
        if (excludedDatasetIds != null) {
            excludedIdList = Arrays.asList(excludedDatasetIds.split(","));
        }
        return excludedIdList;
    }
}
