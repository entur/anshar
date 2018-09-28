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

package no.rutebanken.anshar.data;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract class SiriRepository<T> {

    abstract Collection<T> getAll();

    abstract int getSize();

    abstract Collection<T> getAll(String datasetId);

    abstract Collection<T> getAllUpdates(String requestorId, String datasetId);

    abstract Collection<T> addAll(String datasetId, List<T> ptList);

    abstract T add(String datasetId, T timetableDelivery);

    abstract long getExpiration(T s);

    Set<String> filterIdsByDataset(Set<String> idSet, List<String> excludedDatasetIds, String datasetId) {
        Set<String> requestedIds;
        if (excludedDatasetIds != null && !excludedDatasetIds.isEmpty()) {
            requestedIds = idSet.stream()
                    .filter(key -> {
                        String datasetID = key.substring(0, key.indexOf(":"));
                        return !(excludedDatasetIds.contains(datasetID));
                    })
                    .collect(Collectors.toSet());
        } else {
            requestedIds = idSet.stream()
                    .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                    .collect(Collectors.toSet());
        }
        return requestedIds;
    }

    abstract void clearAllByDatasetId(String datasetId);
}
