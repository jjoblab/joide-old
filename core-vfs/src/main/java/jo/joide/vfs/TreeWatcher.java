package jo.joide.vfs;

/**
 * Surveilleur d'arbre de fichiers : notifie les changements survenus
 * hors de l'éditeur.
 *
 * <p>L'implémentation de bureau s'appuie sur le service de
 * surveillance du système ; l'implémentation Android utilisera
 * l'observateur de fichiers du système. Un surveilleur borné : au-delà
 * de son budget de dossiers surveillés, il renonce — le rattrapage
 * par parcours prend le relais.</p>
 */
public interface TreeWatcher extends AutoCloseable {

    /**
     * Commence la surveillance récursive d'une racine.
     *
     * @param rootDir répertoire réel à surveiller
     * @param listener destinataire des notifications
     * @param token    jeton d'annulation : l'arrêt de la surveillance
     *                 accompagne l'annulation
     */
    void start(java.nio.file.Path rootDir, Listener listener,
               jo.joide.platform.CancelToken token);

    /**
     * Arrête la surveillance et libère les ressources système.
     */
    @Override
    void close();

    /**
     * Destinataire des événements de surveillance.
     */
    interface Listener {

        /**
         * @param relativePath chemin relatif du fichier concerné
         * @param change       nature du changement constaté
         */
        void onFileEvent(String relativePath, FileChange change);

        /**
         * La file d'événements du système a débordé : le ou les
         * dossiers concernés doivent être re-parcourus.
         *
         * @param relativeDir chemin relatif du dossier en débordement
         */
        void onOverflow(String relativeDir);

        /**
         * Le budget de surveillance est épuisé pour ce sous-arbre.
         *
         * @param relativeDir chemin relatif du dossier non surveillé
         */
        void onWatchLimitReached(String relativeDir);
    }
}
