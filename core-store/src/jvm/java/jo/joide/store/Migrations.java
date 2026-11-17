package jo.joide.store;

import java.util.List;

/**
 * Liste des migrations du schéma du store, dans l'ordre des versions.
 *
 * <p>Chaque migration est une transaction : appliquée entièrement ou
 * pas du tout. La version courante est mémorisée dans la table
 * {@code meta} ; une base sans version est une base neuve.</p>
 */
final class Migrations {

    private Migrations() {
    }

    /**
     * @return toutes les migrations connues, de la plus ancienne à la
     *         plus récente
     */
    static List<Migration> all() {
        return List.of(
                new Migration(1, List.of(
                        "CREATE TABLE IF NOT EXISTS vfs_snapshot ("
                                + "root_id TEXT NOT NULL,"
                                + "path TEXT NOT NULL,"
                                + "size INTEGER NOT NULL,"
                                + "mtime_ns INTEGER NOT NULL,"
                                + "file_key TEXT,"
                                + "content_hash TEXT,"
                                + "PRIMARY KEY (root_id, path))",
                        "CREATE TABLE IF NOT EXISTS task_fingerprints ("
                                + "task_key TEXT PRIMARY KEY,"
                                + "fingerprint TEXT NOT NULL,"
                                + "updated_ns INTEGER NOT NULL)",
                        "CREATE TABLE IF NOT EXISTS task_durations ("
                                + "task_type TEXT NOT NULL,"
                                + "task_key TEXT NOT NULL,"
                                + "duration_ms INTEGER NOT NULL,"
                                + "updated_ns INTEGER NOT NULL,"
                                + "PRIMARY KEY (task_type, task_key))",
                        "CREATE TABLE IF NOT EXISTS compiler_state ("
                                + "module TEXT NOT NULL,"
                                + "state_key TEXT NOT NULL,"
                                + "value BLOB NOT NULL,"
                                + "PRIMARY KEY (module, state_key))",
                        "CREATE TABLE IF NOT EXISTS cache_index ("
                                + "fingerprint TEXT PRIMARY KEY,"
                                + "size_bytes INTEGER NOT NULL,"
                                + "created_ns INTEGER NOT NULL,"
                                + "last_used_ns INTEGER NOT NULL)")));
    }
}
