package jo.joide.store;

import java.util.List;

/**
 * Store de build : l'unique persistance des empreintes de tâches, du
 * snapshot de fichiers, des durées historiques, de l'état incrémental
 * des compilateurs et de l'index du cache de sorties.
 *
 * <p>Le store est un cache : toute corruption conduit à une
 * reconstruction vide, jamais à un échec du build. L'implémentation de
 * bureau s'appuie sur SQLite en mode journal écriture d'avance ; le
 * même contrat est prévu sur Android avec la base système.</p>
 *
 * <p>Le store est confiné au fil qui l'a ouvert : les méthodes
 * individuelles sont auto-validées, et {@link #inTransaction(StoreWork)}
 * regroupe plusieurs opérations en une unité atomique. Une interruption
 * du processus laisse toujours le store dans l'état de la dernière
 * transaction validée.</p>
 */
public interface BuildStore extends AutoCloseable {

    /**
     * Ferme le store ; les transactions en cours sont annulées.
     */
    @Override
    void close();

    /**
     * @return vrai si le contenu du store est intègre
     */
    boolean healthy();

    /**
     * Exécute un travail en une seule transaction : soit toutes les
     * opérations sont validées, soit aucune.
     *
     * @param work travail à exécuter
     * @param <T>  type du résultat
     * @return le résultat du travail
     * @throws StoreException si le travail échoue ; la transaction est annulée
     */
    <T> T inTransaction(StoreWork<T> work);

    /**
     * @param rootId identifiant de la racine de snapshot
     * @return les états de fichiers du snapshot de cette racine,
     *         ordonnés par chemin ; liste vide si aucun snapshot
     */
    List<FileStamp> loadSnapshot(String rootId);

    /**
     * Remplace intégralement le snapshot d'une racine.
     *
     * @param rootId  identifiant de la racine de snapshot
     * @param entries états de fichiers complets du nouveau snapshot
     */
    void replaceSnapshot(String rootId, List<FileStamp> entries);

    /**
     * Met à jour le snapshot d'une racine par delta.
     *
     * @param rootId       identifiant de la racine de snapshot
     * @param upserts      états ajoutés ou modifiés
     * @param removedPaths chemins supprimés
     */
    void updateSnapshot(String rootId, List<FileStamp> upserts, List<String> removedPaths);

    /**
     * @param taskKey     clé stable de la tâche
     * @param fingerprint empreinte des entrées de la tâche
     */
    void putFingerprint(String taskKey, String fingerprint);

    /**
     * @param taskKey clé stable de la tâche
     * @return l'empreinte enregistrée, ou {@code null} si absente
     */
    String findFingerprint(String taskKey);

    /**
     * @param taskType   type de la tâche, pour l'estimation par défaut
     * @param taskKey    clé stable de la tâche
     * @param durationMs durée mesurée de la dernière exécution
     */
    void putDuration(String taskType, String taskKey, long durationMs);

    /**
     * @param taskType type de la tâche
     * @param taskKey  clé stable de la tâche
     * @return la dernière durée connue en millisecondes, ou {@code null}
     */
    Long findDuration(String taskType, String taskKey);

    /**
     * @param module   module concerné
     * @param stateKey clé de la donnée incrémentale
     * @param value    valeur binaire opaque
     */
    void putState(String module, String stateKey, byte[] value);

    /**
     * @param module   module concerné
     * @param stateKey clé de la donnée incrémentale
     * @return la valeur binaire, ou {@code null} si absente
     */
    byte[] findState(String module, String stateKey);

    /**
     * @param fingerprint empreinte d'entrées, clé du cache de sorties
     * @param sizeBytes   taille de la sortie mise en cache
     */
    void recordCacheEntry(String fingerprint, long sizeBytes);

    /**
     * Marque une entrée du cache comme récemment utilisée.
     *
     * @param fingerprint empreinte d'entrées
     */
    void touchCacheEntry(String fingerprint);

    /**
     * @param fingerprint empreinte d'entrées
     * @return la taille enregistrée, ou {@code null} si absente
     */
    Long cacheEntrySize(String fingerprint);

    /**
     * Évince les entrées du cache les moins récemment utilisées
     * jusqu'à passer sous le plafond.
     *
     * @param maxTotalBytes plafond de la taille totale des entrées
     * @return la somme des tailles évincées
     */
    long evictCacheBeyond(long maxTotalBytes);
}
