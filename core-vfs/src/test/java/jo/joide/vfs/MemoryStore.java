package jo.joide.vfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jo.joide.store.BuildStore;
import jo.joide.store.CacheEntry;
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
    private final Map<String, Long> cacheCreated = new HashMap<>();
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
        long now = System.nanoTime();
        cacheCreated.put(fingerprint, now);
        cacheLastUsed.put(fingerprint, now);
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
    public List<CacheEntry> listCacheEntries() {
        requireOpen();
        List<CacheEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Long> size : cacheSizes.entrySet()) {
            String fingerprint = size.getKey();
            entries.add(new CacheEntry(fingerprint, size.getValue(),
                    cacheCreated.get(fingerprint), cacheLastUsed.get(fingerprint)));
        }
        entries.sort((a, b) -> Long.compare(b.lastUsedNanos(), a.lastUsedNanos()));
        return entries;
    }

    @Override
    public List<CacheEntry> evictCacheBeyond(long maxTotalBytes) {
        requireOpen();
        List<CacheEntry> byAge = listCacheEntries();
        long kept = 0;
        List<CacheEntry> victims = new ArrayList<>();
        for (CacheEntry entry : byAge) {
            if (kept + entry.sizeBytes() <= maxTotalBytes) {
                kept += entry.sizeBytes();
            } else {
                victims.add(entry);
            }
        }
        for (CacheEntry victim : victims) {
            cacheSizes.remove(victim.fingerprint());
            cacheCreated.remove(victim.fingerprint());
            cacheLastUsed.remove(victim.fingerprint());
        }
        return victims;
    }

    private void requireOpen() {
        if (closed) {
            throw new StoreException("store is closed", null);
        }
    }
}
