package no.rutebanken.anshar.routes.policy;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
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

    private Object syncObject = new Object();
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

    private Void acquireLeadership() throws Exception {

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
                    LOGGER.info("Failed to acquire lock (map={}, key={}, val={}) after {} {}",
                        getLockMapName(),
                            lockKey,
                        getLockValue(),
                        getTryLockTimeout(),
                        getTryLockTimeoutUnit().name()
                    );
                }
            } catch (InterruptedException e) {
                // ignore
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
            } finally {
                if (locked) {
                    locks.remove(lockKey);
                    locks.unlock(lockKey);
                    locked = false;
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
