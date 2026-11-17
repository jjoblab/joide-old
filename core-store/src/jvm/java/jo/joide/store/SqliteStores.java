package jo.joide.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ouverture du store de build sur la machine de bureau : SQLite en
 * mode journal écriture d'avance, migrations versionnées et
 * reconstruction automatique en cas de corruption.
 *
 * <p>Cette implémentation vit dans le source set JVM du module : le
 * pilote repose sur des API absentes d'Android, où le même contrat est
 * implémenté par la base système du démon.</p>
 */
public final class SqliteStores {

    private SqliteStores() {
    }

    /**
     * Ouvre (ou crée) le store au chemin donné.
     *
     * @param dbFile fichier de base de données ; les fichiers annexes
     *               du journal sont créés à côté
     * @return le store ouvert et migré
     * @throws StoreException si l'ouverture échoue après tentative de
     *                        reconstruction
     */
    public static BuildStore open(Path dbFile) {
        try {
            return openFresh(dbFile);
        } catch (StoreException first) {
            // le store est un cache : base corrompue => reconstruction vide
            deleteDatabaseFiles(dbFile);
            try {
                return openFresh(dbFile);
            } catch (StoreException second) {
                throw new StoreException("cannot rebuild store at " + dbFile, second);
            }
        }
    }

    private static BuildStore openFresh(Path dbFile) {
        try {
            if (dbFile.getParent() != null) {
                Files.createDirectories(dbFile.getParent());
            }
            Connection connection = openConnection(dbFile);
            SqliteBuildStore store = new SqliteBuildStore(connection);
            store.applyPragmas();
            store.runMigrations();
            if (!store.quickCheck()) {
                store.close();
                throw new StoreException("integrity check failed for " + dbFile, null);
            }
            return store;
        } catch (java.io.IOException | SQLException e) {
            throw new StoreException("cannot open store at " + dbFile, e);
        }
    }

    private static Connection openConnection(Path dbFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    private static void deleteDatabaseFiles(Path dbFile) {
        String base = dbFile.toAbsolutePath().toString();
        String[] suffixes = {"", "-wal", "-shm", "-journal"};
        for (String suffix : suffixes) {
            try {
                Files.deleteIfExists(Path.of(base + suffix));
            } catch (java.io.IOException e) {
                // meilleure tentative : les fichiers restants seront écrasés
            }
        }
    }

    /**
     * Applique les réglages de durabilité du store.
     *
     * @param statement instruction ouverte sur la base
     * @throws SQLException si un réglage échoue
     */
    static void applyPragmas(Statement statement) throws SQLException {
        statement.execute("PRAGMA journal_mode=WAL");
        statement.execute("PRAGMA synchronous=NORMAL");
        statement.execute("PRAGMA busy_timeout=5000");
    }
}
