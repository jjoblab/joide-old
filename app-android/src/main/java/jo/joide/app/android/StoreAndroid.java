package jo.joide.app.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import jo.joide.store.BuildStore;
import jo.joide.store.CacheEntry;
import jo.joide.store.FileStamp;
import jo.joide.store.StoreException;
import jo.joide.store.StoreWork;

/**
 * Implémentation Android du store de build sur la base système
 * (android.database.sqlite), même contrat et même schéma que le store
 * de bureau (P1 : « l'implémentation Android du même contrat vivra
 * avec le démon ») — le démon du service de build persiste ses
 * empreintes et ses instantanés sur l'appareil, la reprise après mort
 * y lit le disque.
 *
 * <p>Chaque méthode hors transaction est validée individuellement ;
 * {@link #inTransaction(StoreWork)} regroupe les opérations en une
 * transaction SQLite. Le mode journal écriture d'avance garantit qu'un
 * processus tué en pleine écriture laisse la base dans l'état de la
 * dernière transaction validée.</p>
 */
public final class StoreAndroid implements BuildStore {

    private final SQLiteDatabase base;
    private boolean inTransaction;
    private boolean closed;

    private StoreAndroid(SQLiteDatabase base) {
        this.base = base;
    }

    /**
     * Ouvre (ou crée) le store d'un répertoire de sorties.
     *
     * @param outDir répertoire des sorties du build
     * @return le store ouvert
     * @throws StoreException si la base ne s'ouvre pas
     */
    public static StoreAndroid open(Path outDir) {
        try {
            Files.createDirectories(outDir);
        } catch (java.io.IOException e) {
            throw new StoreException("cannot create store directory",
                    e);
        }
        SQLiteDatabase base;
        try {
            base = SQLiteDatabase.openDatabase(
                    outDir.resolve("build.db").toString(), null,
                    SQLiteDatabase.OPEN_READWRITE
                            | SQLiteDatabase.CREATE_IF_NECESSARY);
        } catch (RuntimeException e) {
            throw new StoreException("cannot open store database", e);
        }
        StoreAndroid store = new StoreAndroid(base);
        try {
            base.execSQL("PRAGMA journal_mode=WAL");
            base.execSQL("CREATE TABLE IF NOT EXISTS vfs_snapshot ("
                    + "root_id TEXT NOT NULL,"
                    + "path TEXT NOT NULL,"
                    + "size INTEGER NOT NULL,"
                    + "mtime_ns INTEGER NOT NULL,"
                    + "file_key TEXT,"
                    + "content_hash TEXT,"
                    + "PRIMARY KEY (root_id, path))");
            base.execSQL("CREATE TABLE IF NOT EXISTS task_fingerprints ("
                    + "task_key TEXT PRIMARY KEY,"
                    + "fingerprint TEXT NOT NULL,"
                    + "updated_ns INTEGER NOT NULL)");
            base.execSQL("CREATE TABLE IF NOT EXISTS task_durations ("
                    + "task_type TEXT NOT NULL,"
                    + "task_key TEXT NOT NULL,"
                    + "duration_ms INTEGER NOT NULL,"
                    + "updated_ns INTEGER NOT NULL,"
                    + "PRIMARY KEY (task_type, task_key))");
            base.execSQL("CREATE TABLE IF NOT EXISTS compiler_state ("
                    + "module TEXT NOT NULL,"
                    + "state_key TEXT NOT NULL,"
                    + "value BLOB NOT NULL,"
                    + "PRIMARY KEY (module, state_key))");
            base.execSQL("CREATE TABLE IF NOT EXISTS cache_index ("
                    + "fingerprint TEXT PRIMARY KEY,"
                    + "size_bytes INTEGER NOT NULL,"
                    + "created_ns INTEGER NOT NULL,"
                    + "last_used_ns INTEGER NOT NULL)");
            return store;
        } catch (RuntimeException e) {
            store.close();
            throw new StoreException("cannot create store schema", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            base.close();
        } catch (RuntimeException e) {
            // fermeture au mieux : le journal d'écriture d'avance
            // protège les données
        }
    }

    @Override
    public boolean healthy() {
        if (closed) {
            return false;
        }
        try (Cursor result = base.rawQuery(
                "PRAGMA integrity_check", null)) {
            return result.moveToFirst()
                    && "ok".equalsIgnoreCase(result.getString(0));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public <T> T inTransaction(StoreWork<T> work) {
        Objects.requireNonNull(work, "work");
        if (inTransaction) {
            return work.apply(this);
        }
        inTransaction = true;
        base.beginTransaction();
        try {
            T outcome = work.apply(this);
            base.setTransactionSuccessful();
            return outcome;
        } catch (RuntimeException e) {
            throw e;
        } finally {
            base.endTransaction();
            inTransaction = false;
        }
    }

    @Override
    public List<FileStamp> loadSnapshot(String rootId) {
        requireOpen();
        List<FileStamp> entries = new ArrayList<>();
        try (Cursor result = base.rawQuery(
                "SELECT path, size, mtime_ns, file_key, content_hash"
                        + " FROM vfs_snapshot WHERE root_id = ?"
                        + " ORDER BY path",
                new String[]{rootId})) {
            while (result.moveToNext()) {
                entries.add(new FileStamp(result.getString(0),
                        result.getLong(1), result.getLong(2),
                        result.getString(3), result.getString(4)));
            }
            return entries;
        } catch (RuntimeException e) {
            throw new StoreException(
                    "cannot load snapshot for " + rootId, e);
        }
    }

    @Override
    public void replaceSnapshot(String rootId, List<FileStamp> entries) {
        Objects.requireNonNull(entries, "entries");
        requireOpen();
        inTransaction(store -> {
            base.delete("vfs_snapshot", "root_id = ?",
                    new String[]{rootId});
            for (FileStamp entry : entries) {
                insererEmpreinte(rootId, entry);
            }
            return null;
        });
    }

    @Override
    public void updateSnapshot(String rootId, List<FileStamp> upserts,
                               List<String> removedPaths) {
        Objects.requireNonNull(upserts, "upserts");
        Objects.requireNonNull(removedPaths, "removedPaths");
        requireOpen();
        inTransaction(store -> {
            for (FileStamp entry : upserts) {
                base.delete("vfs_snapshot",
                        "root_id = ? AND path = ?",
                        new String[]{rootId, entry.path()});
                insererEmpreinte(rootId, entry);
            }
            for (String removed : removedPaths) {
                base.delete("vfs_snapshot",
                        "root_id = ? AND path = ?",
                        new String[]{rootId, removed});
            }
            return null;
        });
    }

    private void insererEmpreinte(String rootId, FileStamp entry) {
        ContentValues valeurs = new ContentValues();
        valeurs.put("root_id", rootId);
        valeurs.put("path", entry.path());
        valeurs.put("size", entry.size());
        valeurs.put("mtime_ns", entry.mtimeNs());
        valeurs.put("file_key", entry.fileKey());
        valeurs.put("content_hash", entry.contentHash());
        base.insertOrThrow("vfs_snapshot", null, valeurs);
    }

    @Override
    public void putFingerprint(String taskKey, String fingerprint) {
        requireOpen();
        runAtomically(() -> {
            ContentValues valeurs = new ContentValues();
            valeurs.put("task_key", taskKey);
            valeurs.put("fingerprint", fingerprint);
            valeurs.put("updated_ns", nowNanos());
            base.insertWithOnConflict("task_fingerprints", null,
                    valeurs, SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    @Override
    public String findFingerprint(String taskKey) {
        requireOpen();
        try (Cursor result = base.rawQuery(
                "SELECT fingerprint FROM task_fingerprints"
                        + " WHERE task_key = ?",
                new String[]{taskKey})) {
            return result.moveToFirst() ? result.getString(0) : null;
        } catch (RuntimeException e) {
            throw new StoreException(
                    "cannot find fingerprint for " + taskKey, e);
        }
    }

    @Override
    public void putDuration(String taskType, String taskKey,
                            long durationMs) {
        requireOpen();
        runAtomically(() -> {
            ContentValues valeurs = new ContentValues();
            valeurs.put("task_type", taskType);
            valeurs.put("task_key", taskKey);
            valeurs.put("duration_ms", durationMs);
            valeurs.put("updated_ns", nowNanos());
            base.insertWithOnConflict("task_durations", null, valeurs,
                    SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    @Override
    public Long findDuration(String taskType, String taskKey) {
        requireOpen();
        try (Cursor result = base.rawQuery(
                "SELECT duration_ms FROM task_durations"
                        + " WHERE task_type = ? AND task_key = ?",
                new String[]{taskType, taskKey})) {
            return result.moveToFirst() ? result.getLong(0) : null;
        } catch (RuntimeException e) {
            throw new StoreException(
                    "cannot find duration for " + taskKey, e);
        }
    }

    @Override
    public void putState(String module, String stateKey, byte[] value) {
        Objects.requireNonNull(value, "value");
        requireOpen();
        runAtomically(() -> {
            ContentValues valeurs = new ContentValues();
            valeurs.put("module", module);
            valeurs.put("state_key", stateKey);
            valeurs.put("value", value);
            base.insertWithOnConflict("compiler_state", null, valeurs,
                    SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    @Override
    public byte[] findState(String module, String stateKey) {
        requireOpen();
        try (Cursor result = base.rawQuery(
                "SELECT value FROM compiler_state"
                        + " WHERE module = ? AND state_key = ?",
                new String[]{module, stateKey})) {
            return result.moveToFirst() ? result.getBlob(0) : null;
        } catch (RuntimeException e) {
            throw new StoreException(
                    "cannot find state for " + module, e);
        }
    }

    @Override
    public void recordCacheEntry(String fingerprint, long sizeBytes) {
        requireOpen();
        runAtomically(() -> {
            long maintenant = nowNanos();
            ContentValues valeurs = new ContentValues();
            valeurs.put("fingerprint", fingerprint);
            valeurs.put("size_bytes", sizeBytes);
            valeurs.put("created_ns", maintenant);
            valeurs.put("last_used_ns", maintenant);
            base.insertWithOnConflict("cache_index", null, valeurs,
                    SQLiteDatabase.CONFLICT_REPLACE);
        });
    }

    @Override
    public void touchCacheEntry(String fingerprint) {
        requireOpen();
        runAtomically(() -> base.execSQL(
                "UPDATE cache_index SET last_used_ns = " + nowNanos()
                        + " WHERE fingerprint = ?",
                new Object[]{fingerprint}));
    }

    @Override
    public Long cacheEntrySize(String fingerprint) {
        requireOpen();
        try (Cursor result = base.rawQuery(
                "SELECT size_bytes FROM cache_index"
                        + " WHERE fingerprint = ?",
                new String[]{fingerprint})) {
            return result.moveToFirst() ? result.getLong(0) : null;
        } catch (RuntimeException e) {
            throw new StoreException(
                    "cannot read cache entry " + fingerprint, e);
        }
    }

    @Override
    public List<CacheEntry> listCacheEntries() {
        requireOpen();
        List<CacheEntry> entries = new ArrayList<>();
        try (Cursor result = base.rawQuery(
                "SELECT fingerprint, size_bytes, created_ns,"
                        + " last_used_ns FROM cache_index"
                        + " ORDER BY last_used_ns DESC", null)) {
            while (result.moveToNext()) {
                entries.add(new CacheEntry(result.getString(0),
                        result.getLong(1), result.getLong(2),
                        result.getLong(3)));
            }
            return entries;
        } catch (RuntimeException e) {
            throw new StoreException("cannot list cache entries", e);
        }
    }

    @Override
    public List<CacheEntry> evictCacheBeyond(long maxTotalBytes) {
        requireOpen();
        return inTransaction(store -> eviction(
                listCacheEntries(), maxTotalBytes));
    }

    /**
     * Politique d'éviction pure : les entrées de l'utilisation la plus
     * récente à la plus ancienne, on garde tout tant que le plafond
     * tient, puis tout le reste part — de la moins récemment utilisée
     * à la plus récente. Testable sur la JVM sans base.
     *
     * @param entries        entrées ordonnées de la plus récemment
     *                       utilisée à la plus ancienne
     * @param maxTotalBytes plafond de la taille totale des entrées
     * @return les entrées à évincer, de la moins récemment utilisée
     *         à la plus récente
     */
    static List<CacheEntry> eviction(List<CacheEntry> entries,
                                     long maxTotalBytes) {
        long total = 0;
        List<CacheEntry> gardees = new ArrayList<>();
        for (CacheEntry entry : entries) {
            if (total + entry.sizeBytes() <= maxTotalBytes) {
                total += entry.sizeBytes();
                gardees.add(entry);
            }
        }
        List<CacheEntry> evincees = new ArrayList<>(entries);
        evincees.removeAll(gardees);
        // de la moins récemment utilisée à la plus récente : l'ordre
        // inverse de la liste (qui va du plus récent au plus ancien)
        List<CacheEntry> ordre = new ArrayList<>(evincees);
        java.util.Collections.reverse(ordre);
        return ordre;
    }

    private void runAtomically(Runnable work) {
        inTransaction(store -> {
            work.run();
            return null;
        });
    }

    private void requireOpen() {
        if (closed) {
            throw new StoreException("store is closed");
        }
    }

    private static long nowNanos() {
        return System.nanoTime();
    }
}
