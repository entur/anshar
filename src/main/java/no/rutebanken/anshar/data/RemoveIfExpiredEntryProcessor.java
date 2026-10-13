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

import com.hazelcast.map.EntryProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;

import java.util.Map;

/**
 * Removes the entry only if the current value is expired.
 *
 * Expiry is checked by the member that owns the entry, at the time of removal - an update that
 * has been received after the entry was found to be expired is therefore not removed.
 */
public class RemoveIfExpiredEntryProcessor<T> implements EntryProcessor<SiriObjectStorageKey, T, Boolean> {

    private final SiriDataType siriDataType;
    private final long maxValidityMillis;

    public RemoveIfExpiredEntryProcessor(SiriDataType siriDataType, long maxValidityMillis) {
        this.siriDataType = siriDataType;
        this.maxValidityMillis = maxValidityMillis;
    }

    @Override
    public Boolean process(Map.Entry<SiriObjectStorageKey, T> entry) {
        T value = entry.getValue();
        if (value == null) {
            // Already removed
            return Boolean.TRUE;
        }
        if (getRepository().isExpired(value, maxValidityMillis)) {
            entry.setValue(null);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * Backup is only processed when the entry has been removed from the owner
     */
    @Override
    public EntryProcessor<SiriObjectStorageKey, T, Boolean> getBackupProcessor() {
        return new RemoveEntryProcessor<>();
    }

    @SuppressWarnings("unchecked")
    private SiriRepository<T> getRepository() {
        final Class<? extends SiriRepository> repositoryClass;
        switch (siriDataType) {
            case ESTIMATED_TIMETABLE:
                repositoryClass = EstimatedTimetables.class;
                break;
            case VEHICLE_MONITORING:
                repositoryClass = VehicleActivities.class;
                break;
            case SITUATION_EXCHANGE:
                repositoryClass = Situations.class;
                break;
            case FACILITY_MONITORING:
                repositoryClass = Facilities.class;
                break;
            default:
                throw new IllegalStateException("No repository for " + siriDataType);
        }
        return ApplicationContextHolder.getContext().getBean(repositoryClass);
    }

    private static class RemoveEntryProcessor<T> implements EntryProcessor<SiriObjectStorageKey, T, Boolean> {

        @Override
        public Boolean process(Map.Entry<SiriObjectStorageKey, T> entry) {
            entry.setValue(null);
            return Boolean.TRUE;
        }

        @Override
        public EntryProcessor<SiriObjectStorageKey, T, Boolean> getBackupProcessor() {
            return null;
        }
    }
}
