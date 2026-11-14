package jo.joide.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implémentation du store sur SQLite en mode journal écriture d'avance.
 *
 * <p>Chaque méthode hors transaction est validée individuellement ;
 * dans une transaction, les opérations rejoignent la transaction
 * courante du fil appelant. Le mode écriture d'avance garantit qu'un
 * processus interrompu laisse la base dans l'état de la dernière
 * transaction validée, sans écriture à moitié appliquée.</p>
 */
final class SqliteBuildStore implements BuildStore {

    private final Connection connection;
    private boolean inTransaction;
    private boolean closed;

    SqliteBuildStore(Connection connection) {
        this.connection = connection;
    }

    void applyPragmas() {
        try (Statement statement = connection.createStatement()) {
            SqliteStores.applyPragmas(statement);
        } catch (SQLException e) {
            close();
            throw new StoreException("cannot apply store pragmas", e);
        }
    }

    void runMigrations() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS meta ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        } catch (SQLException e) {
            close();
            throw new StoreException("cannot create meta table", e);
        }
        int current = readVersion();
        for (Migration migration : Migrations.all()) {
            if (migration.version() <= current) {
                continue;
            }
            inTransaction = true;
            try (Statement statement = connection.createStatement()) {
                statement.execute("BEGIN");
                for (String sql : migration.statements()) {
                    statement.execute(sql);
                }
                statement.execute("INSERT OR REPLACE INTO meta(key, value) VALUES ("
                        + "'schema_version', '" + migration.version() + "')");
                statement.execute("COMMIT");
            } catch (SQLException e) {
                rollbackQuietly();
                close();
                throw new StoreException(
                        "migration " + migration.version() + " failed", e);
            } finally {
                inTransaction = false;
            }
        }
    }

    private int readVersion() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT value FROM meta WHERE key = 'schema_version'")) {
            return result.next() ? Integer.parseInt(result.getString(1)) : 0;
        } catch (SQLException | NumberFormatException e) {
            return 0;
        }
    }

    boolean quickCheck() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
            return result.next() && "ok".equalsIgnoreCase(result.getString(1));
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            connection.close();
        } catch (SQLException e) {
            // fermeture au mieux : le journal écriture d'avance protège les données
        }
    }

    @Override
    public boolean healthy() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            return result.next() && "ok".equalsIgnoreCase(result.getString(1));
        } catch (SQLException e) {
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
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            T outcome = work.apply(this);
            statement.execute("COMMIT");
            return outcome;
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly();
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new StoreException("transaction failed", e);
        } finally {
            inTransaction = false;
        }
    }

    @Override
    public List<FileStamp> loadSnapshot(String rootId) {
        requireOpen();
        List<FileStamp> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT path, size, mtime_ns, file_key, content_hash"
                        + " FROM vfs_snapshot WHERE root_id = ? ORDER BY path")) {
            statement.setString(1, rootId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new FileStamp(
                            result.getString(1),
                            result.getLong(2),
                            result.getLong(3),
                            result.getString(4),
                            result.getString(5)));
                }
            }
            return entries;
        } catch (SQLException e) {
            throw new StoreException("cannot load snapshot for " + rootId, e);
        }
    }

    @Override
    public void replaceSnapshot(String rootId, List<FileStamp> entries) {
        requireOpen();
        runAtomically(store -> {
            try (PreparedStatement wipe = connection.prepareStatement(
                    "DELETE FROM vfs_snapshot WHERE root_id = ?");
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO vfs_snapshot"
                                 + " (root_id, path, size, mtime_ns, file_key, content_hash)"
                                 + " VALUES (?, ?, ?, ?, ?, ?)")) {
                wipe.setString(1, rootId);
                wipe.executeUpdate();
                for (FileStamp entry : entries) {
                    insert.setString(1, rootId);
                    insert.setString(2, entry.path());
                    insert.setLong(3, entry.size());
                    insert.setLong(4, entry.mtimeNs());
                    insert.setString(5, entry.fileKey());
                    insert.setString(6, entry.contentHash());
                    insert.addBatch();
                }
                insert.executeBatch();
            } catch (SQLException e) {
                throw new StoreException("cannot replace snapshot for " + rootId, e);
            }
            return null;
        });
    }

    @Override
    public void updateSnapshot(String rootId, List<FileStamp> upserts,
                               List<String> removedPaths) {
        requireOpen();
        runAtomically(store -> {
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO vfs_snapshot"
                            + " (root_id, path, size, mtime_ns, file_key, content_hash)"
                            + " VALUES (?, ?, ?, ?, ?, ?)"
                            + " ON CONFLICT(root_id, path) DO UPDATE SET"
                            + " size = excluded.size, mtime_ns = excluded.mtime_ns,"
                            + " file_key = excluded.file_key,"
                            + " content_hash = excluded.content_hash");
                 PreparedStatement remove = connection.prepareStatement(
                         "DELETE FROM vfs_snapshot"
                                 + " WHERE root_id = ? AND path = ?")) {
                for (FileStamp entry : upserts) {
                    upsert.setString(1, rootId);
                    upsert.setString(2, entry.path());
                    upsert.setLong(3, entry.size());
                    upsert.setLong(4, entry.mtimeNs());
                    upsert.setString(5, entry.fileKey());
                    upsert.setString(6, entry.contentHash());
                    upsert.addBatch();
                }
                upsert.executeBatch();
                for (String path : removedPaths) {
                    remove.setString(1, rootId);
                    remove.setString(2, path);
                    remove.addBatch();
                }
                remove.executeBatch();
            } catch (SQLException e) {
                throw new StoreException("cannot update snapshot for " + rootId, e);
            }
            return null;
        });
    }

    @Override
    public void putFingerprint(String taskKey, String fingerprint) {
        requireOpen();
        runAtomically(store -> {
            executeUpsert(
                    "INSERT INTO task_fingerprints (task_key, fingerprint, updated_ns)"
                            + " VALUES (?, ?, ?)"
                            + " ON CONFLICT(task_key) DO UPDATE SET"
                            + " fingerprint = excluded.fingerprint,"
                            + " updated_ns = excluded.updated_ns",
                    statement -> {
                        statement.setString(1, taskKey);
                        statement.setString(2, fingerprint);
                        statement.setLong(3, System.nanoTime());
                    });
            return null;
        });
    }

    @Override
    public String findFingerprint(String taskKey) {
        requireOpen();
        return queryString("SELECT fingerprint FROM task_fingerprints WHERE task_key = ?",
                statement -> statement.setString(1, taskKey));
    }

    @Override
    public void putDuration(String taskType, String taskKey, long durationMs) {
        requireOpen();
        runAtomically(store -> {
            executeUpsert(
                    "INSERT INTO task_durations (task_type, task_key, duration_ms, updated_ns)"
                            + " VALUES (?, ?, ?, ?)"
                            + " ON CONFLICT(task_type, task_key) DO UPDATE SET"
                            + " duration_ms = excluded.duration_ms,"
                            + " updated_ns = excluded.updated_ns",
                    statement -> {
                        statement.setString(1, taskType);
                        statement.setString(2, taskKey);
                        statement.setLong(3, durationMs);
                        statement.setLong(4, System.nanoTime());
                    });
            return null;
        });
    }

    @Override
    public Long findDuration(String taskType, String taskKey) {
        requireOpen();
        return queryLong("SELECT duration_ms FROM task_durations"
                        + " WHERE task_type = ? AND task_key = ?",
                statement -> {
                    statement.setString(1, taskType);
                    statement.setString(2, taskKey);
                });
    }

    @Override
    public void putState(String module, String stateKey, byte[] value) {
        requireOpen();
        runAtomically(store -> {
            executeUpsert(
                    "INSERT INTO compiler_state (module, state_key, value)"
                            + " VALUES (?, ?, ?)"
                            + " ON CONFLICT(module, state_key) DO UPDATE SET"
                            + " value = excluded.value",
                    statement -> {
                        statement.setString(1, module);
                        statement.setString(2, stateKey);
                        statement.setBytes(3, value);
                    });
            return null;
        });
    }

    @Override
    public byte[] findState(String module, String stateKey) {
        requireOpen();
        return queryBytes("SELECT value FROM compiler_state"
                        + " WHERE module = ? AND state_key = ?",
                statement -> {
                    statement.setString(1, module);
                    statement.setString(2, stateKey);
                });
    }

    @Override
    public void recordCacheEntry(String fingerprint, long sizeBytes) {
        requireOpen();
        runAtomically(store -> {
            long now = System.nanoTime();
            executeUpsert(
                    "INSERT INTO cache_index"
                            + " (fingerprint, size_bytes, created_ns, last_used_ns)"
                            + " VALUES (?, ?, ?, ?)"
                            + " ON CONFLICT(fingerprint) DO UPDATE SET"
                            + " size_bytes = excluded.size_bytes,"
                            + " last_used_ns = excluded.last_used_ns",
                    statement -> {
                        statement.setString(1, fingerprint);
                        statement.setLong(2, sizeBytes);
                        statement.setLong(3, now);
                        statement.setLong(4, now);
                    });
            return null;
        });
    }

    @Override
    public void touchCacheEntry(String fingerprint) {
        requireOpen();
        runAtomically(store -> {
            executeUpsert(
                    "UPDATE cache_index SET last_used_ns = ? WHERE fingerprint = ?",
                    statement -> {
                        statement.setLong(1, System.nanoTime());
                        statement.setString(2, fingerprint);
                    });
            return null;
        });
    }

    @Override
    public Long cacheEntrySize(String fingerprint) {
        requireOpen();
        return queryLong("SELECT size_bytes FROM cache_index WHERE fingerprint = ?",
                statement -> statement.setString(1, fingerprint));
    }

    @Override
    public long evictCacheBeyond(long maxTotalBytes) {
        requireOpen();
        return runAtomically(store -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT fingerprint, size_bytes FROM cache_index"
                            + " ORDER BY last_used_ns DESC");
                 PreparedStatement drop = connection.prepareStatement(
                         "DELETE FROM cache_index WHERE fingerprint = ?")) {
                List<String> victims = new ArrayList<>();
                List<Long> victimSizes = new ArrayList<>();
                long kept = 0;
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        String fingerprint = result.getString(1);
                        long size = result.getLong(2);
                        if (kept + size <= maxTotalBytes) {
                            kept += size;
                        } else {
                            victims.add(fingerprint);
                            victimSizes.add(size);
                        }
                    }
                }
                for (String victim : victims) {
                    drop.setString(1, victim);
                    drop.addBatch();
                }
                drop.executeBatch();
                long freed = 0;
                for (long size : victimSizes) {
                    freed += size;
                }
                return freed;
            } catch (SQLException e) {
                throw new StoreException("cannot evict cache entries", e);
            }
        });
    }

    private void requireOpen() {
        if (closed) {
            throw new StoreException("store is closed", null);
        }
    }

    /**
     * Exécute une opération en la joignant à la transaction courante ou
     * en en ouvrant une nouvelle.
     */
    private <T> T runAtomically(StoreWork<T> operation) {
        if (inTransaction) {
            return operation.apply(this);
        }
        inTransaction = true;
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            T outcome = operation.apply(this);
            statement.execute("COMMIT");
            return outcome;
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly();
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new StoreException("store operation failed", e);
        } finally {
            inTransaction = false;
        }
    }

    private void rollbackQuietly() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException e) {
            // le mode écriture d'avance rétablit l'état à la réouverture
        }
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private void executeUpsert(String sql, Binder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("store upsert failed", e);
        }
    }

    private String queryString(String sql, Binder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new StoreException("store query failed", e);
        }
    }

    private Long queryLong(String sql, Binder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new StoreException("store query failed", e);
        }
    }

    private byte[] queryBytes(String sql, Binder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getBytes(1) : null;
            }
        } catch (SQLException e) {
            throw new StoreException("store query failed", e);
        }
    }
}
