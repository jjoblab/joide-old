package jo.joide.build.jvm.tasks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jo.joide.store.BuildStore;
import jo.joide.store.CacheEntry;
import jo.joide.store.FileStamp;
import jo.joide.store.StoreWork;

/**
 * Store de build en mémoire pour les tests du pipeline : sémantique
 * transactionnelle minimale (annulation = retour à l'état du dernier
 * commit), sans persistance.
 */
public final class MemoryBuildStore implements BuildStore {

    private static final class State {
        Map<String, List<FileStamp>> snapshots = new LinkedHashMap<>();
        Map<String, String> fingerprints = new LinkedHashMap<>();
        Map<String, Map<String, Long>> durations = new LinkedHashMap<>();
        Map<String, Map<String, byte[]>> states = new LinkedHashMap<>();
        Map<String, Long> cacheSizes = new LinkedHashMap<>();
        Deque<String> cacheOrder = new ArrayDeque<>();
    }

    private State committed = new State();
    private State working;
    private boolean open = true;

    private State state() {
        return working == null ? committed : working;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean healthy() {
        return open;
    }

    @Override
    public <T> T inTransaction(StoreWork<T> work) {
        working = copy(committed);
        try {
            T result = work.apply(this);
            committed = working;
            return result;
        } finally {
            working = null;
        }
    }

    private static State copy(State source) {
        State copy = new State();
        copy.snapshots.putAll(source.snapshots);
        copy.fingerprints.putAll(source.fingerprints);
        copy.durations.putAll(source.durations);
        copy.states.putAll(source.states);
        copy.cacheSizes.putAll(source.cacheSizes);
        copy.cacheOrder.addAll(source.cacheOrder);
        return copy;
    }

    @Override
    public List<FileStamp> loadSnapshot(String rootId) {
        return state().snapshots.getOrDefault(rootId, List.of());
    }

    @Override
    public void replaceSnapshot(String rootId, List<FileStamp> entries) {
        state().snapshots.put(rootId, new ArrayList<>(entries));
    }

    @Override
    public void updateSnapshot(String rootId, List<FileStamp> upserts,
                               List<String> removedPaths) {
        List<FileStamp> entries = new ArrayList<>(
                state().snapshots.getOrDefault(rootId, List.of()));
        Map<String, FileStamp> byPath = new LinkedHashMap<>();
        for (FileStamp stamp : entries) {
            byPath.put(stamp.path(), stamp);
        }
        for (FileStamp upsert : upserts) {
            byPath.put(upsert.path(), upsert);
        }
        for (String removed : removedPaths) {
            byPath.remove(removed);
        }
        state().snapshots.put(rootId,
                new ArrayList<>(byPath.values()));
    }

    @Override
    public void putFingerprint(String taskKey, String fingerprint) {
        state().fingerprints.put(taskKey, fingerprint);
    }

    @Override
    public String findFingerprint(String taskKey) {
        return state().fingerprints.get(taskKey);
    }

    @Override
    public void putDuration(String taskType, String taskKey,
                            long durationMs) {
        state().durations.computeIfAbsent(taskType,
                k -> new LinkedHashMap<>()).put(taskKey, durationMs);
    }

    @Override
    public Long findDuration(String taskType, String taskKey) {
        Map<String, Long> durations = state().durations.get(taskType);
        return durations == null ? null : durations.get(taskKey);
    }

    @Override
    public void putState(String module, String stateKey, byte[] value) {
        state().states.computeIfAbsent(module,
                k -> new LinkedHashMap<>()).put(stateKey, value.clone());
    }

    @Override
    public byte[] findState(String module, String stateKey) {
        Map<String, byte[]> states = state().states.get(module);
        byte[] value = states == null ? null : states.get(stateKey);
        return value == null ? null : value.clone();
    }

    @Override
    public void recordCacheEntry(String fingerprint, long sizeBytes) {
        state().cacheSizes.put(fingerprint, sizeBytes);
        state().cacheOrder.remove(fingerprint);
        state().cacheOrder.addLast(fingerprint);
    }

    @Override
    public void touchCacheEntry(String fingerprint) {
        if (state().cacheSizes.containsKey(fingerprint)) {
            state().cacheOrder.remove(fingerprint);
            state().cacheOrder.addLast(fingerprint);
        }
    }

    @Override
    public Long cacheEntrySize(String fingerprint) {
        return state().cacheSizes.get(fingerprint);
    }

    @Override
    public List<CacheEntry> listCacheEntries() {
        List<CacheEntry> entries = new ArrayList<>();
        for (String fingerprint : state().cacheOrder) {
            Long size = state().cacheSizes.get(fingerprint);
            entries.add(new CacheEntry(fingerprint,
                    size == null ? 0 : size, 0, 0));
        }
        return entries;
    }

    @Override
    public List<CacheEntry> evictCacheBeyond(long maxTotalBytes) {
        long total = 0;
        for (Long size : state().cacheSizes.values()) {
            total += size == null ? 0 : size;
        }
        List<CacheEntry> evicted = new ArrayList<>();
        while (total > maxTotalBytes && !state().cacheOrder.isEmpty()) {
            String oldest = state().cacheOrder.pollFirst();
            Long size = state().cacheSizes.remove(oldest);
            if (size != null) {
                total -= size;
                evicted.add(new CacheEntry(oldest, size, 0, 0));
            }
        }
        return evicted;
    }
}
