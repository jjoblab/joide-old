package jo.joide.agent.espace;

/**
 * Le port d'espace de travail de l'agent (lot O, ADR-059) : le SEUL
 * point de contact entre l'agent et le projet — lectures
 * overlay-preferred (le texte de l'éditeur ouvert prime sur le
 * disque), écritures par les services, exécution bornée, mémoire du
 * projet, questions à l'utilisateur.
 *
 * <p>L'hôte n'implémente que ce qu'il sait faire : chaque méthode
 * hors le socle fichier a un DÉFAUT DÉGRADÉ qui lève
 * {@link EspaceNonSupporte} — l'outil le rend honnêtement au modèle,
 * jamais une liste vide qui se lirait « rien trouvé » (leçon 7 de
 * l'ADR-É9).</p>
 *
 * <p>Le confinement est PAR CONSTRUCTION : tous les chemins sont
 * RELATIFS à la racine du projet, validés par {@link CheminsConfines}
 * avant d'atteindre le port — aucun chemin absolu, aucun « .. », la
 * leçon du risque n° 2 de l'ADR-É9.</p>
 */
public interface EspaceTravailAgent extends AutoCloseable {

    /** Le fichier mémoire du projet (sa racine). */
    String FICHIER_MEMOIRE = "AGENTS.md";

    /**
     * Lit un fichier du projet, OVERLAY d'abord : le texte de
     * l'éditeur ouvert prime sur le disque (tampon et disque
     * synchrones).
     *
     * @param chemin chemin relatif (séparateurs « / »)
     * @return le contenu texte
     * @throws java.io.IOException si le fichier n'existe pas ou la
     *                             lecture échoue
     */
    String lireFichier(String chemin) throws java.io.IOException;

    /**
     * Liste les entrées d'un dossier.
     *
     * @param chemin chemin relatif du dossier ("" pour la racine)
     * @return les noms, dossiers suffixés « / », triés
     * @throws java.io.IOException si le dossier n'existe pas
     */
    java.util.List<String> listerDossier(String chemin)
            throws java.io.IOException;

    /**
     * Cherche un motif textuel dans les fichiers ouvrables du projet.
     *
     * @param motif               le texte cherché
     * @param expressionReguliere vrai : le motif est une regex
     * @param plafond             le maximum d'occurrences rendues
     * @return les occurrences (fichier, ligne, extrait), bornées
     * @throws java.io.IOException si le parcours échoue
     */
    default java.util.List<OccurrenceEspace> chercherTexte(
            String motif, boolean expressionReguliere, int plafond)
            throws java.io.IOException {
        throw new EspaceNonSupporte("text search");
    }

    /**
     * Cherche où un symbole est déclaré.
     *
     * @param nom le nom du symbole
     * @return les déclarations trouvées, bornées
     */
    default java.util.List<OccurrenceEspace> trouverSymbole(String nom) {
        throw new EspaceNonSupporte("symbol search");
    }

    /**
     * Les diagnostics d'un fichier (overlay-preferred : le texte
     * ouvert est diagnostiqué).
     *
     * @param chemin chemin relatif
     * @return les diagnostics, bornés
     */
    default java.util.List<DiagnosticEspace> diagnostics(String chemin) {
        throw new EspaceNonSupporte("diagnostics");
    }

    /**
     * @return un aperçu honnête du projet (modules, fichiers, état)
     */
    default String apercuProjet() {
        throw new EspaceNonSupporte("project overview");
    }

    /**
     * Écrit un fichier (création ou remplacement) par les services
     * d'édition.
     *
     * @param chemin   chemin relatif
     * @param contenu  le contenu complet
     * @throws java.io.IOException si l'écriture échoue
     */
    void ecrireFichier(String chemin, String contenu)
            throws java.io.IOException;

    /**
     * Crée un fichier — refuse s'il existe déjà.
     *
     * @param chemin  chemin relatif
     * @param contenu le contenu initial
     * @throws java.io.IOException si le fichier existe ou l'écriture échoue
     */
    void creerFichier(String chemin, String contenu)
            throws java.io.IOException;

    /**
     * Édite un fichier par occurrence EXACTE unique : l'ancien texte
     * doit apparaître exactement une fois.
     *
     * @param chemin  chemin relatif
     * @param ancien  le texte exact à remplacer
     * @param nouveau le texte de remplacement
     * @throws java.io.IOException si l'occurrence n'est pas unique ou l'écriture échoue
     */
    void editerFichier(String chemin, String ancien, String nouveau)
            throws java.io.IOException;

    /**
     * Renomme/déplace un chemin.
     *
     * @param de   chemin relatif source
     * @param vers chemin relatif cible
     * @throws java.io.IOException si la cible existe ou le déplacement échoue
     */
    void renommerChemin(String de, String vers) throws java.io.IOException;

    /**
     * Supprime un fichier ou dossier.
     *
     * @param chemin chemin relatif
     * @throws java.io.IOException si la suppression échoue
     */
    void supprimerChemin(String chemin) throws java.io.IOException;

    /**
     * Ajoute une dépendance externe à un module : résolution P4,
     * lockfile, modèle déclaratif mis à jour.
     *
     * @param module      le chemin du module (ex. « :app »)
     * @param coordonnees « groupe:artefact:version »
     * @return vrai si la dépendance est nouvelle et résolue
     */
    default boolean ajouterDependance(String module, String coordonnees) {
        throw new EspaceNonSupporte("add dependency");
    }

    /**
     * Va à la définition du symbole à la position donnée.
     *
     * @param fichier chemin relatif
     * @param ligne   ligne (1-based)
     * @param colonne colonne (1-based)
     * @return la position de définition, ou vide
     */
    default java.util.Optional<PositionEspace> definition(
            String fichier, int ligne, int colonne) {
        throw new EspaceNonSupporte("go to definition");
    }

    /**
     * Les références du symbole à la position donnée.
     */
    default java.util.List<PositionEspace> references(
            String fichier, int ligne, int colonne) {
        throw new EspaceNonSupporte("find references");
    }

    /**
     * Renomme le symbole à la position donnée.
     */
    default void renommerSymbole(String fichier, int ligne, int colonne,
                                 String nouveauNom) {
        throw new EspaceNonSupporte("rename symbol");
    }

    /**
     * Formate un fichier : retourne le texte formaté (l'écriture
     * appartient à l'appelant — un outil, pas le port).
     *
     * @param fichier chemin relatif
     * @return le texte formaté complet
     */
    default String formaterFichier(String fichier) {
        throw new EspaceNonSupporte("format file");
    }

    /**
     * Exécute une cible du projet, bornée dans le temps.
     *
     * @param cible            la cible (tâche, test, programme)
     * @param delaiMaxSecondes le plafond d'exécution
     * @return le résultat borné (la QUEUE de la sortie est conservée)
     */
    default ResultatExecution executer(String cible,
                                        long delaiMaxSecondes) {
        throw new EspaceNonSupporte("run task");
    }

    /**
     * Lit la mémoire du projet (AGENTS.md à la racine, vide si absent).
     */
    default String lireMemoire() throws java.io.IOException {
        throw new EspaceNonSupporte("memory");
    }

    /**
     * Écrit la mémoire du projet.
     */
    default void ecrireMemoire(String contenu) throws java.io.IOException {
        throw new EspaceNonSupporte("memory");
    }

    /**
     * Pose des questions à l'utilisateur (cartes paginées côté hôte).
     *
     * @param questions une à quatre questions, deux à quatre options chacune
     * @return les réponses, une par ligne « question : réponse »
     */
    default String demanderUtilisateur(
            java.util.List<QuestionUtilisateur> questions) {
        throw new EspaceNonSupporte("ask user");
    }

    @Override
    default void close() {
    }
}
