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

package no.rutebanken.anshar.routes.policy;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.map.IMap;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class InterruptibleHazelcastRoutePolicy extends HazelcastRoutePolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterruptibleHazelcastRoutePolicy.class);

    private final HazelcastInstance instance;

    private IMap<String, String> locks;

    private final Object syncObject = new Object();
    private ExecutorService executorService;
    private Future<Void> future;

    public InterruptibleHazelcastRoutePolicy(HazelcastInstance instance) {
        super(instance, false);
        this.instance = instance;
    }

    @Override
    protected void doStart() throws Exception {
        // validate
        StringHelper.notEmpty(getLockMapName(), "lockMapName", this);
        StringHelper.notEmpty(getLockKey(), "lockKey", this);
        StringHelper.notEmpty(getLockValue(), "lockValue", this);

        executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, this.getClass().getSimpleName());

        locks = instance.getMap(getLockMapName());
        future = executorService.submit(this::acquireLeadership);

    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(true);
            future = null;
        }

        instance.shutdown();

        getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

        super.doStop();
    }

    public void releaseLeadership() {
        synchronized(syncObject) {
            syncObject.notifyAll();
        }
    }

    private Void acquireLeadership() {

        String lockKey = getLockKey();

        LOGGER.info("Starting to acquire lock (map={}, key={}, val={}) timeout {} {}",
                getLockMapName(),
                lockKey,
                getLockValue(),
                getTryLockTimeout(),
                getTryLockTimeoutUnit().name()
        );

        boolean locked = false;
        while (isRunAllowed()) {
            try {
                locked = locks.tryLock(lockKey, getTryLockTimeout(), getTryLockTimeoutUnit());
                if (locked) {
                    locks.put(getLockKey(), getLockValue());
                    setLeader(true);

                    synchronized(syncObject) {
                        // Calling wait() will block this thread until another thread
                        // calls notify() on the object.
                        syncObject.wait();
                    }
                } else {
                    LOGGER.debug("Failed to acquire lock (map={}, key={}, val={}) after {} {}",
                        getLockMapName(),
                            lockKey,
                        getLockValue(),
                        getTryLockTimeout(),
                        getTryLockTimeoutUnit().name()
                    );
                }
            } catch (InterruptedException e) {
                // ignore
            } catch (HazelcastInstanceNotActiveException e) {
                if (isStoppingOrStopped()) {
                    //ignore
                } else {
                    throw e;
                }
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
            } finally {
                if (locked) {
                    locks.remove(lockKey);
                    locks.unlock(lockKey);
                    releaseLeadership();
                    locked = false;

                    LOGGER.info("Released lock(map={}, key={}, val={}) timeout {} {}",
                            getLockMapName(),
                            lockKey,
                            getLockValue(),
                            getTryLockTimeout(),
                            getTryLockTimeoutUnit().name()
                    );
                }
                setLeader(false);
            }
        }
        LOGGER.info("Finished trying to acquire lock(map={}, key={}, val={}) timeout {} {}",
                getLockMapName(),
                lockKey,
                getLockValue(),
                getTryLockTimeout(),
                getTryLockTimeoutUnit().name()
        );

        return null;
    }
}
