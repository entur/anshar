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

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.metrics.MetricsService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.MessageRefStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class Situations implements SiriRepository<PtSituationElement> {
    private final Logger logger = LoggerFactory.getLogger(Situations.class);

    @Autowired
    private IMap<String,PtSituationElement> situations;

    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<String>> changesMap;


    @Autowired
    @Qualifier("getLastSxUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private AnsharConfiguration configuration;

    /**
     * @return All situations
     */
    public Collection<PtSituationElement> getAll() {
        return situations.values();
    }

    public int getSize() {
        return situations.size();
    }


    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        situations.clear();
    }

    public Siri createServiceDelivery(String requestorId, String datasetId) {
        int maxSize = configuration.getDefaultMaxSize();
        if (datasetId != null) {
            maxSize = Integer.MAX_VALUE;
        }
        return createServiceDelivery(requestorId, datasetId, maxSize);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
//            trackingPeriodMinutes = configuration.getAdHocTrackingPeriodMinutes();
            isAdHocRequest = true;
        }

        // Get all relevant ids
        Set<String> allIds = new HashSet<>();
        Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            situations.keySet().forEach(idSet::add);
        }

        //Filter by datasetId
        Set<String> requestedIds = idSet.stream()
                .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                .collect(Collectors.toSet());

        Set<String> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(idSet::remove);

        Collection<PtSituationElement> values = situations.getAll(sizeLimitedIds).values();
        Siri siri = siriObjectFactory.createSXServiceDelivery(values);

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {

            siri.getServiceDelivery().setMoreData(isMoreData);

            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);

            //Update change-tracker
            changesMap.set(requestorId, idSet);
            lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<PtSituationElement> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        Map<String, PtSituationElement> datasetIdSpecific = new HashMap<>();
        situations.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            PtSituationElement element = situations.get(key);
            if (element != null) {
                datasetIdSpecific.put(key, element);
            }
        });

        return new ArrayList<>(datasetIdSpecific.values());
    }


    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<PtSituationElement> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
            if (idSet != null) {
                Set<String> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.startsWith(datasetId + ":")).forEach(datasetFilteredIdSet::add);
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }
                Collection<PtSituationElement> changes = situations.getAll(datasetFilteredIdSet).values();

                // Data may have been updated
                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                existingSet.removeAll(idSet);
                changesMap.set(requestorId, existingSet);


                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {
                logger.info("Returning all to requestorRef {}", requestorId);
            }
            changesMap.set(requestorId, new HashSet<>());
        }

        return getAll(datasetId);
    }

    public long getExpiration(PtSituationElement situationElement) {
        List<HalfOpenTimestampOutputRangeStructure> validityPeriods = situationElement.getValidityPeriods();

        ZonedDateTime expiry = null;

        if (validityPeriods != null) {
            for (HalfOpenTimestampOutputRangeStructure validity : validityPeriods) {

                //Find latest validity
                if (expiry == null) {
                    expiry = validity.getEndTime();
                } else if (validity != null && validity.getEndTime().isAfter(expiry)) {
                    expiry = validity.getEndTime();
                }
            }
        }

        if (expiry != null) {
            return ZonedDateTime.now().until(expiry.plus(configuration.getSxGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
        } else {
            return -1;
        }
    }

    public Collection<PtSituationElement> addAll(String datasetId, List<PtSituationElement> sxList) {
        Set<String> changes = new HashSet<>();

        Counter alreadyExpiredCounter = new CounterImpl(0);
        sxList.forEach(situation -> {
            String key = createKey(datasetId, situation);

            //TODO: Determine if newer situation has already been handled

            long expiration = getExpiration(situation);
            if (expiration > 0) { //expiration < 0 => already expired
                situations.set(key, situation, expiration, TimeUnit.MILLISECONDS);
                changes.add(key);
            } else if (situations.containsKey(key)) {
                // Situation is no longer valid
                situations.remove(key);
            }
            if (expiration < 0) {
                alreadyExpiredCounter.increment();
            }

        });
        logger.info("Updated {} (of {}) :: Already expired: {},", changes.size(), sxList.size(), alreadyExpiredCounter.getValue());

        metricsService.registerIncomingData(SiriDataType.SITUATION_EXCHANGE, datasetId, situations);

        changesMap.keySet().forEach(requestor -> {
            if (lastUpdateRequested.get(requestor) != null) {
                Set<String> tmpChanges = changesMap.get(requestor);
                tmpChanges.addAll(changes);
                changesMap.set(requestor, tmpChanges);
            } else {
                changesMap.remove(requestor);
            }
        });
        return situations.getAll(changes).values();
    }

    public PtSituationElement add(String datasetId, PtSituationElement situation) {
        if (situation == null) {
            return null;
        }
        List<PtSituationElement> situationList = new ArrayList<>();
        situationList.add(situation);
        addAll(datasetId, situationList);
        return situations.get(createKey(datasetId, situation));
    }

    private static String createKey(String datasetId, PtSituationElement element) {
        StringBuilder key = new StringBuilder();

        key.append(datasetId).append(":")
                .append((element.getSituationNumber() != null ? element.getSituationNumber().getValue() : "null"))
                .append(":")
                .append((element.getParticipantRef() != null ? element.getParticipantRef().getValue() :"null"));
        return key.toString();
    }
}
