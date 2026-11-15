package jo.joide.store;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Store de bureau en journal à ajouts : prototype alternatif au store
 * SQLite, même contrat {@link BuildStore}.
 *
 * <p>Chaque transaction est mise en tampon puis écrite d'un seul
 * bloc, terminée par une somme de contrôle : un processus tué en
 * pleine transaction laisse un bloc incomplet que la relecture
 * écarte — l'état observé est toujours celui de la dernière
 * transaction entièrement écrite. Un fichier d'état complet est
 * réécrit atomiquement quand le journal dépasse son seuil, puis le
 * journal est vidé ; l'état complet vit aussi en mémoire, comme les
 * pages du store SQLite.</p>
 */
public final class JournalBuildStore implements BuildStore {

    private static final byte OP_SNAPSHOT = 1;
    private static final byte OP_FINGERPRINT = 2;
    private static final byte OP_DURATION = 3;
    private static final byte OP_STATE = 4;
    private static final byte OP_SNAPSHOT_CLEAR = 5;
    private static final byte OP_SNAPSHOT_UPSERT = 6;
    private static final byte OP_SNAPSHOT_REMOVE = 7;
    private static final byte OP_CACHE_PUT = 8;
    private static final byte OP_CACHE_TOUCH = 9;
    private static final byte OP_CACHE_DROP = 10;

    private static final long JOURNAL_THRESHOLD_BYTES = 1024 * 1024;

    private final Path directory;
    private final Path stateFile;
    private final Path journalFile;

    private final Map<String, Map<String, FileStamp>> snapshots = new HashMap<>();
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Map<String, long[]> durations = new HashMap<>();
    private final Map<String, byte[]> states = new HashMap<>();
    private final Map<String, long[]> cacheEntries = new HashMap<>();
    private final Map<String, Long> cacheLastUsed = new HashMap<>();

    private OutputStream journal;
    private boolean closed;
    private boolean inTransaction;
    private ByteArrayOutputStream transactionBuffer;

    /**
     * Ouvre (ou crée) le store dans un dossier.
     *
     * @param directory dossier du store, créé si absent
     * @return le store ouvert
     * @throws StoreException si le dossier ou les fichiers sont
     *                        illisibles
     */
    public static JournalBuildStore open(Path directory) {
        try {
            Files.createDirectories(directory);
            JournalBuildStore store = new JournalBuildStore(directory);
            store.load();
            store.journal = Files.newOutputStream(store.journalFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return store;
        } catch (IOException failure) {
            throw new StoreException("cannot open journal store", failure);
        }
    }

    private JournalBuildStore(Path directory) {
        this.directory = directory;
        this.stateFile = directory.resolve("store.state");
        this.journalFile = directory.resolve("store.journal");
    }

    @Override
    public void close() {
        ensureOpen();
        try {
            flushTransaction();
            journal.close();
        } catch (IOException failure) {
            throw new StoreException("cannot close journal store", failure);
        } finally {
            closed = true;
        }
    }

    @Override
    public boolean healthy() {
        return !closed;
    }

    @Override
    public <T> T inTransaction(StoreWork<T> work) {
        ensureOpen();
        if (inTransaction) {
            return work.apply(this);
        }
        inTransaction = true;
        transactionBuffer = new ByteArrayOutputStream();
        try {
            T result = work.apply(this);
            flushTransaction();
            return result;
        } catch (IOException writeFailure) {
            reloadAfterAbortedTransaction();
            throw new StoreException("cannot write journal transaction",
                    writeFailure);
        } catch (RuntimeException failure) {
            // la transaction est abandonnée : le tampon est jeté et
            // l'état mémoire est rejoué depuis le disque — jamais un
            // état à moitié appliqué
            reloadAfterAbortedTransaction();
            throw failure;
        } finally {
            inTransaction = false;
            transactionBuffer = null;
        }
    }

    private void flushTransaction() throws IOException {
        if (transactionBuffer == null || transactionBuffer.size() == 0) {
            return;
        }
        byte[] block = transactionBuffer.toByteArray();
        transactionBuffer.reset();
        CRC32 crc = new CRC32();
        crc.update(block, 0, block.length);
        DataOutputStream out = new DataOutputStream(journal);
        out.writeInt(block.length);
        out.write(block);
        out.writeLong(crc.getValue());
        out.flush();
        journal.flush();
        if (journalFile.toFile().length() > JOURNAL_THRESHOLD_BYTES) {
            compact();
        }
    }

    private void reloadAfterAbortedTransaction() {
        try {
            snapshots.clear();
            fingerprints.clear();
            durations.clear();
            states.clear();
            cacheEntries.clear();
            cacheLastUsed.clear();
            load();
        } catch (RuntimeException failure) {
            throw new StoreException(
                    "cannot recover after aborted transaction", failure);
        }
    }

    private void load() {
        try {
            if (Files.exists(stateFile)) {
                try (DataInputStream in = new DataInputStream(
                        Files.newInputStream(stateFile))) {
                    readStateBlock(in);
                }
            }
            if (!Files.exists(journalFile)) {
                return;
            }
            try (DataInputStream in = new DataInputStream(
                    Files.newInputStream(journalFile))) {
                while (true) {
                    int length;
                    try {
                        length = in.readInt();
                    } catch (EOFException done) {
                        return;
                    }
                    if (length <= 0) {
                        return;
                    }
                    byte[] block = new byte[length];
                    in.readFully(block);
                    long expectedCrc;
                    try {
                        expectedCrc = in.readLong();
                    } catch (EOFException truncated) {
                        // transaction interrompue : tout le bloc est
                        // écarté, comme la suite du journal
                        return;
                    }
                    CRC32 crc = new CRC32();
                    crc.update(block, 0, block.length);
                    if (crc.getValue() != expectedCrc) {
                        return;
                    }
                    replay(block);
                }
            }
        } catch (IOException failure) {
            // le store est un cache : un état illisible repart vide
            snapshots.clear();
            fingerprints.clear();
            durations.clear();
            states.clear();
            cacheEntries.clear();
            cacheLastUsed.clear();
        }
    }

    private void replay(byte[] block) throws IOException {
        DataInputStream in = new DataInputStream(
                new java.io.ByteArrayInputStream(block));
        while (in.available() > 0) {
            byte op = in.readByte();
            switch (op) {
                case OP_SNAPSHOT -> readSnapshot(in);
                case OP_FINGERPRINT -> fingerprints.put(in.readUTF(), in.readUTF());
                case OP_DURATION -> durations.put(in.readUTF(),
                        new long[] {in.readLong(), in.readLong()});
                case OP_STATE -> states.put(in.readUTF(), readBytes(in));
                case OP_SNAPSHOT_CLEAR -> snapshots.put(in.readUTF(),
                        new HashMap<>());
                case OP_SNAPSHOT_UPSERT -> {
                    String root = in.readUTF();
                    String upsertPath = in.readUTF();
                    snapshots.computeIfAbsent(root, key -> new HashMap<>())
                            .put(upsertPath, readStamp(upsertPath, in));
                }
                case OP_SNAPSHOT_REMOVE -> {
                    String root = in.readUTF();
                    Map<String, FileStamp> snapshot = snapshots.get(root);
                    if (snapshot != null) {
                        int count = in.readInt();
                        for (int i = 0; i < count; i++) {
                            snapshot.remove(in.readUTF());
                        }
                    } else {
                        for (int i = 0; i < in.readInt(); i++) {
                            in.readUTF();
                        }
                    }
                }
                case OP_CACHE_PUT -> {
                    String fingerprint = in.readUTF();
                    cacheEntries.put(fingerprint,
                            new long[] {in.readLong(), in.readLong()});
                    cacheLastUsed.put(fingerprint, in.readLong());
                }
                case OP_CACHE_TOUCH -> {
                    String fingerprint = in.readUTF();
                    cacheLastUsed.put(fingerprint, in.readLong());
                }
                case OP_CACHE_DROP -> {
                    int count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        String fingerprint = in.readUTF();
                        cacheEntries.remove(fingerprint);
                        cacheLastUsed.remove(fingerprint);
                    }
                }
                default -> throw new IOException("unknown op: " + op);
            }
        }
    }

    @Override
    public List<FileStamp> loadSnapshot(String rootId) {
        ensureOpen();
        Map<String, FileStamp> snapshot = snapshots.get(rootId);
        return snapshot == null ? List.of()
                : new ArrayList<>(new TreeMap<>(snapshot).values());
    }

    @Override
    public void replaceSnapshot(String rootId, List<FileStamp> entries) {
        ensureOpen();
        Map<String, FileStamp> snapshot = new HashMap<>();
        for (FileStamp entry : entries) {
            snapshot.put(entry.path(), entry);
        }
        snapshots.put(rootId, snapshot);
        log(op -> {
            op.writeByte(OP_SNAPSHOT);
            op.writeUTF(rootId);
            op.writeInt(entries.size());
            for (FileStamp entry : entries) {
                op.writeUTF(entry.path());
                writeStamp(op, entry);
            }
        });
    }

    @Override
    public void updateSnapshot(String rootId, List<FileStamp> upserts,
                               List<String> removedPaths) {
        ensureOpen();
        Map<String, FileStamp> snapshot =
                snapshots.computeIfAbsent(rootId, key -> new HashMap<>());
        for (FileStamp upsert : upserts) {
            snapshot.put(upsert.path(), upsert);
        }
        for (String removed : removedPaths) {
            snapshot.remove(removed);
        }
        log(op -> {
            op.writeByte(OP_SNAPSHOT_UPSERT);
            op.writeUTF(rootId);
            op.writeInt(upserts.size());
            for (FileStamp upsert : upserts) {
                op.writeUTF(upsert.path());
                writeStamp(op, upsert);
            }
            op.writeByte(OP_SNAPSHOT_REMOVE);
            op.writeUTF(rootId);
            op.writeInt(removedPaths.size());
            for (String removed : removedPaths) {
                op.writeUTF(removed);
            }
        });
    }

    @Override
    public void putFingerprint(String taskKey, String fingerprint) {
        ensureOpen();
        fingerprints.put(taskKey, fingerprint);
        log(op -> {
            op.writeByte(OP_FINGERPRINT);
            op.writeUTF(taskKey);
            op.writeUTF(fingerprint);
        });
    }

    @Override
    public String findFingerprint(String taskKey) {
        ensureOpen();
        return fingerprints.get(taskKey);
    }

    @Override
    public void putDuration(String taskType, String taskKey, long durationMs) {
        ensureOpen();
        durations.put(taskType + "/" + taskKey,
                new long[] {durationMs, System.nanoTime()});
        log(op -> {
            op.writeByte(OP_DURATION);
            op.writeUTF(taskType + "/" + taskKey);
            op.writeLong(durationMs);
            op.writeLong(System.nanoTime());
        });
    }

    @Override
    public Long findDuration(String taskType, String taskKey) {
        ensureOpen();
        long[] entry = durations.get(taskType + "/" + taskKey);
        return entry == null ? null : entry[0];
    }

    @Override
    public void putState(String module, String stateKey, byte[] value) {
        ensureOpen();
        states.put(module + "/" + stateKey, value);
        log(op -> {
            op.writeByte(OP_STATE);
            op.writeUTF(module + "/" + stateKey);
            op.writeInt(value.length);
            op.write(value);
        });
    }

    @Override
    public byte[] findState(String module, String stateKey) {
        ensureOpen();
        return states.get(module + "/" + stateKey);
    }

    @Override
    public void recordCacheEntry(String fingerprint, long sizeBytes) {
        ensureOpen();
        long now = System.nanoTime();
        cacheEntries.put(fingerprint, new long[] {sizeBytes, now});
        cacheLastUsed.put(fingerprint, now);
        log(op -> {
            op.writeByte(OP_CACHE_PUT);
            op.writeUTF(fingerprint);
            op.writeLong(sizeBytes);
            op.writeLong(now);
            op.writeLong(now);
        });
    }

    @Override
    public void touchCacheEntry(String fingerprint) {
        ensureOpen();
        long now = System.nanoTime();
        cacheLastUsed.put(fingerprint, now);
        log(op -> {
            op.writeByte(OP_CACHE_TOUCH);
            op.writeUTF(fingerprint);
            op.writeLong(now);
        });
    }

    @Override
    public Long cacheEntrySize(String fingerprint) {
        ensureOpen();
        long[] entry = cacheEntries.get(fingerprint);
        return entry == null ? null : entry[0];
    }

    @Override
    public List<CacheEntry> listCacheEntries() {
        ensureOpen();
        List<CacheEntry> entries = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : cacheEntries.entrySet()) {
            entries.add(new CacheEntry(entry.getKey(), entry.getValue()[0],
                    entry.getValue()[1],
                    cacheLastUsed.getOrDefault(entry.getKey(), 0L)));
        }
        entries.sort((a, b) -> Long.compare(b.lastUsedNanos(), a.lastUsedNanos()));
        return entries;
    }

    @Override
    public List<CacheEntry> evictCacheBeyond(long maxTotalBytes) {
        ensureOpen();
        List<CacheEntry> victims = new ArrayList<>();
        long kept = 0;
        for (CacheEntry entry : listCacheEntries()) {
            if (kept + entry.sizeBytes() <= maxTotalBytes) {
                kept += entry.sizeBytes();
            } else {
                victims.add(entry);
            }
        }
        if (victims.isEmpty()) {
            return victims;
        }
        List<String> victimKeys = new ArrayList<>();
        for (CacheEntry victim : victims) {
            victimKeys.add(victim.fingerprint());
            cacheEntries.remove(victim.fingerprint());
            cacheLastUsed.remove(victim.fingerprint());
        }
        log(op -> {
            op.writeByte(OP_CACHE_DROP);
            op.writeInt(victimKeys.size());
            for (String key : victimKeys) {
                op.writeUTF(key);
            }
        });
        return victims;
    }

    private void compact() throws IOException {
        Path staging = directory.resolve("store.state.staging");
        try (DataOutputStream out = new DataOutputStream(
                Files.newOutputStream(staging, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING))) {
            writeStateBlock(out);
        }
        Files.move(staging, stateFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        journal.close();
        journal = Files.newOutputStream(journalFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeStateBlock(DataOutputStream out) throws IOException {
        for (Map.Entry<String, Map<String, FileStamp>> snapshot
                : snapshots.entrySet()) {
            out.writeByte(OP_SNAPSHOT);
            out.writeUTF(snapshot.getKey());
            out.writeInt(snapshot.getValue().size());
            for (FileStamp stamp : snapshot.getValue().values()) {
                out.writeUTF(stamp.path());
                writeStamp(out, stamp);
            }
        }
        for (Map.Entry<String, String> fingerprint : fingerprints.entrySet()) {
            out.writeByte(OP_FINGERPRINT);
            out.writeUTF(fingerprint.getKey());
            out.writeUTF(fingerprint.getValue());
        }
        for (Map.Entry<String, long[]> duration : durations.entrySet()) {
            out.writeByte(OP_DURATION);
            out.writeUTF(duration.getKey());
            out.writeLong(duration.getValue()[0]);
            out.writeLong(duration.getValue()[1]);
        }
        for (Map.Entry<String, byte[]> state : states.entrySet()) {
            out.writeByte(OP_STATE);
            out.writeUTF(state.getKey());
            out.writeInt(state.getValue().length);
            out.write(state.getValue());
        }
        for (Map.Entry<String, long[]> entry : cacheEntries.entrySet()) {
            out.writeByte(OP_CACHE_PUT);
            out.writeUTF(entry.getKey());
            out.writeLong(entry.getValue()[0]);
            out.writeLong(entry.getValue()[1]);
            out.writeLong(cacheLastUsed.getOrDefault(entry.getKey(), 0L));
        }
    }

    private void readStateBlock(DataInputStream in) throws IOException {
        while (true) {
            byte op;
            try {
                op = in.readByte();
            } catch (EOFException done) {
                return;
            }
            switch (op) {
                case OP_SNAPSHOT -> readSnapshot(in);
                case OP_FINGERPRINT -> fingerprints.put(in.readUTF(),
                        in.readUTF());
                case OP_DURATION -> durations.put(in.readUTF(),
                        new long[] {in.readLong(), in.readLong()});
                case OP_STATE -> states.put(in.readUTF(), readBytes(in));
                case OP_CACHE_PUT -> {
                    String fingerprint = in.readUTF();
                    cacheEntries.put(fingerprint,
                            new long[] {in.readLong(), in.readLong()});
                    cacheLastUsed.put(fingerprint, in.readLong());
                }
                default -> throw new IOException("unknown op in state: " + op);
            }
        }
    }

    private void readSnapshot(DataInputStream in) throws IOException {
        String root = in.readUTF();
        int count = in.readInt();
        Map<String, FileStamp> snapshot =
                snapshots.computeIfAbsent(root, key -> new HashMap<>());
        for (int i = 0; i < count; i++) {
            String path = in.readUTF();
            snapshot.put(path, readStamp(path, in));
        }
    }

    private static FileStamp readStamp(String path, DataInputStream in)
            throws IOException {
        return new FileStamp(path, in.readLong(), in.readLong(),
                readNullable(in), readNullable(in));
    }

    private static void writeStamp(DataOutputStream out, FileStamp stamp)
            throws IOException {
        out.writeLong(stamp.size());
        out.writeLong(stamp.mtimeNs());
        writeNullable(out, stamp.fileKey());
        writeNullable(out, stamp.contentHash());
    }

    private static String readNullable(DataInputStream in) throws IOException {
        boolean present = in.readBoolean();
        return present ? in.readUTF() : null;
    }

    private static void writeNullable(DataOutputStream out, String value)
            throws IOException {
        if (value == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeUTF(value);
        }
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private interface OperationWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private void log(OperationWriter writer) {
        // chaque écriture est une transaction : mise en tampon,
        // écriture d'un bloc, somme de contrôle
        inTransaction(store -> {
            try {
                DataOutputStream out = new DataOutputStream(transactionBuffer);
                writer.write(out);
            } catch (IOException impossible) {
                throw new StoreException("cannot buffer journal entry",
                        impossible);
            }
            return null;
        });
    }

    private void ensureOpen() {
        if (closed) {
            throw new StoreException("store is closed", null);
        }
    }
}
