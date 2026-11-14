package jo.joide.vfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jo.joide.store.BuildStore;
import jo.joide.store.FileStamp;
import jo.joide.store.StoreException;
import jo.joide.store.StoreWork;

/**
 * Store en mémoire pour les tests du module : même contrat que le
 * store réel, sans disque.
 */
final class MemoryStore implements BuildStore {

    private final Map<String, Map<String, FileStamp>> snapshots = new HashMap<>();
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Map<String, byte[]> states = new HashMap<>();
    private final Map<String, Long> durations = new HashMap<>();
    private final Map<String, Long> cacheSizes = new HashMap<>();
    private final Map<String, Long> cacheLastUsed = new HashMap<>();
    private boolean closed;
    private boolean failNextTransaction;

    /** Provoque l'échec de la prochaine transaction, pour les tests. */
    void failNextTransaction() {
        failNextTransaction = true;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean healthy() {
        return !closed;
    }

    @Override
    public <T> T inTransaction(StoreWork<T> work) {
        requireOpen();
        if (failNextTransaction) {
            failNextTransaction = false;
            throw new StoreException("injected failure", null);
        }
        return work.apply(this);
    }

    @Override
    public List<FileStamp> loadSnapshot(String rootId) {
        requireOpen();
        Map<String, FileStamp> snapshot = snapshots.get(rootId);
        if (snapshot == null) {
            return List.of();
        }
        return new ArrayList<>(snapshot.values());
    }

    @Override
    public void replaceSnapshot(String rootId, List<FileStamp> entries) {
        requireOpen();
        Map<String, FileStamp> snapshot = new HashMap<>();
        for (FileStamp entry : entries) {
            snapshot.put(entry.path(), entry);
        }
        snapshots.put(rootId, snapshot);
    }

    @Override
    public void updateSnapshot(String rootId, List<FileStamp> upserts,
                               List<String> removedPaths) {
        requireOpen();
        Map<String, FileStamp> snapshot =
                snapshots.computeIfAbsent(rootId, k -> new HashMap<>());
        for (FileStamp upsert : upserts) {
            snapshot.put(upsert.path(), upsert);
        }
        for (String path : removedPaths) {
            snapshot.remove(path);
        }
    }

    @Override
    public void putFingerprint(String taskKey, String fingerprint) {
        requireOpen();
        fingerprints.put(taskKey, fingerprint);
    }

    @Override
    public String findFingerprint(String taskKey) {
        requireOpen();
        return fingerprints.get(taskKey);
    }

    @Override
    public void putDuration(String taskType, String taskKey, long durationMs) {
        requireOpen();
        durations.put(taskType + "/" + taskKey, durationMs);
    }

    @Override
    public Long findDuration(String taskType, String taskKey) {
        requireOpen();
        return durations.get(taskType + "/" + taskKey);
    }

    @Override
    public void putState(String module, String stateKey, byte[] value) {
        requireOpen();
        states.put(module + "/" + stateKey, value);
    }

    @Override
    public byte[] findState(String module, String stateKey) {
        requireOpen();
        return states.get(module + "/" + stateKey);
    }

    @Override
    public void recordCacheEntry(String fingerprint, long sizeBytes) {
        requireOpen();
        cacheSizes.put(fingerprint, sizeBytes);
        cacheLastUsed.put(fingerprint, System.nanoTime());
    }

    @Override
    public void touchCacheEntry(String fingerprint) {
        requireOpen();
        cacheLastUsed.put(fingerprint, System.nanoTime());
    }

    @Override
    public Long cacheEntrySize(String fingerprint) {
        requireOpen();
        return cacheSizes.get(fingerprint);
    }

    @Override
    public long evictCacheBeyond(long maxTotalBytes) {
        requireOpen();
        List<String> byAge = new ArrayList<>(cacheSizes.keySet());
        byAge.sort((a, b) -> Long.compare(cacheLastUsed.get(b), cacheLastUsed.get(a)));
        long kept = 0;
        long freed = 0;
        List<String> victims = new ArrayList<>();
        for (String fingerprint : byAge) {
            long size = cacheSizes.get(fingerprint);
            if (kept + size <= maxTotalBytes) {
                kept += size;
            } else {
                victims.add(fingerprint);
                freed += size;
            }
        }
        for (String victim : victims) {
            cacheSizes.remove(victim);
            cacheLastUsed.remove(victim);
        }
        return freed;
    }

    private void requireOpen() {
        if (closed) {
            throw new StoreException("store is closed", null);
        }
    }
}
