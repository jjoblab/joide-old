package jo.joide.vfs;

/**
 * Nature d'un changement de fichier signalé par une source du journal.
 */
public enum FileChange {
    /** Fichier apparu. */
    CREATED,

    /** Fichier existant dont le contenu a changé. */
    MODIFIED,

    /** Fichier disparu. */
    DELETED
}
