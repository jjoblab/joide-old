package jo.joide.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jo.joide.store.BuildStore;
import jo.joide.store.CacheEntry;
import jo.joide.store.FileStamp;
import jo.joide.store.StoreWork;

/**
 * Store en mémoire pour les tests du moteur : même contrat que le
 * store réel, sans disque.
 */
public final class EngineMemoryStore implements BuildStore {

    private final Map<String, Map<String, FileStamp>> snapshots = new HashMap<>();
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Map<String, byte[]> states = new HashMap<>();
    private final Map<String, Long> durations = new HashMap<>();
    private final Map<String, Long> cacheSizes = new HashMap<>();
    private final Map<String, Long> cacheCreated = new HashMap<>();
    private final Map<String, Long> cacheLastUsed = new HashMap<>();
    private boolean closed;

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
        return work.apply(this);
    }

    @Override
    public List<FileStamp> loadSnapshot(String rootId) {
        requireOpen();
        Map<String, FileStamp> snapshot = snapshots.get(rootId);
        return snapshot == null ? List.of() : new ArrayList<>(snapshot.values());
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
                snapshots.computeIfAbsent(rootId, key -> new HashMap<>());
        for (FileStamp upsert : upserts) {
            snapshot.put(upsert.path(), upsert);
        }
        for (String removed : removedPaths) {
            snapshot.remove(removed);
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
            entries.add(new CacheEntry(size.getKey(), size.getValue(),
                    cacheCreated.get(size.getKey()),
                    cacheLastUsed.get(size.getKey())));
        }
        entries.sort((a, b) -> Long.compare(b.lastUsedNanos(), a.lastUsedNanos()));
        return entries;
    }

    @Override
    public List<CacheEntry> evictCacheBeyond(long maxTotalBytes) {
        requireOpen();
        List<CacheEntry> byAge = listCacheEntries();
        List<CacheEntry> victims = new ArrayList<>();
        long kept = 0;
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
            throw new jo.joide.store.StoreException("store is closed", null);
        }
    }

    /**
     * Enveloppe de traçage : une fois armée, tout appel au store
     * déclenche une assertion. Prouve l'absence totale d'accès.
     */
    public static final class Guard implements BuildStore {

        private final BuildStore delegate;
        private volatile boolean armed;
        private int violations;

        /**
         * @param delegate store réel délégué
         */
        public Guard(BuildStore delegate) {
            this.delegate = delegate;
        }

        /** Arme le contrôle : tout appel ultérieur est une violation. */
        public void arm() {
            armed = true;
        }

        /** @return le nombre d'appels observés pendant l'armement */
        public int violations() {
            return violations;
        }

        private void check() {
            if (armed) {
                violations++;
                throw new AssertionError(
                        "forbidden store access while guard is armed (call #"
                                + violations + ")");
            }
        }

        @Override
        public void close() {
            check();
            delegate.close();
        }

        @Override
        public boolean healthy() {
            check();
            return delegate.healthy();
        }

        @Override
        public <T> T inTransaction(StoreWork<T> work) {
            check();
            return delegate.inTransaction(work);
        }

        @Override
        public List<FileStamp> loadSnapshot(String rootId) {
            check();
            return delegate.loadSnapshot(rootId);
        }

        @Override
        public void replaceSnapshot(String rootId, List<FileStamp> entries) {
            check();
            delegate.replaceSnapshot(rootId, entries);
        }

        @Override
        public void updateSnapshot(String rootId, List<FileStamp> upserts,
                                   List<String> removedPaths) {
            check();
            delegate.updateSnapshot(rootId, upserts, removedPaths);
        }

        @Override
        public void putFingerprint(String taskKey, String fingerprint) {
            check();
            delegate.putFingerprint(taskKey, fingerprint);
        }

        @Override
        public String findFingerprint(String taskKey) {
            check();
            return delegate.findFingerprint(taskKey);
        }

        @Override
        public void putDuration(String taskType, String taskKey, long durationMs) {
            check();
            delegate.putDuration(taskType, taskKey, durationMs);
        }

        @Override
        public Long findDuration(String taskType, String taskKey) {
            check();
            return delegate.findDuration(taskType, taskKey);
        }

        @Override
        public void putState(String module, String stateKey, byte[] value) {
            check();
            delegate.putState(module, stateKey, value);
        }

        @Override
        public byte[] findState(String module, String stateKey) {
            check();
            return delegate.findState(module, stateKey);
        }

        @Override
        public void recordCacheEntry(String fingerprint, long sizeBytes) {
            check();
            delegate.recordCacheEntry(fingerprint, sizeBytes);
        }

        @Override
        public void touchCacheEntry(String fingerprint) {
            check();
            delegate.touchCacheEntry(fingerprint);
        }

        @Override
        public Long cacheEntrySize(String fingerprint) {
            check();
            return delegate.cacheEntrySize(fingerprint);
        }

        @Override
        public List<CacheEntry> listCacheEntries() {
            check();
            return delegate.listCacheEntries();
        }

        @Override
        public List<CacheEntry> evictCacheBeyond(long maxTotalBytes) {
            check();
            return delegate.evictCacheBeyond(maxTotalBytes);
        }
    }
}
