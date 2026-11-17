package jo.joide.agent.espace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les checkpoints (ADR-É9, leçon 13) : l'espace de travail enveloppé
 * — chaque écriture prend un instantané du fichier AU PREMIER
 * TOUCHER du tour ; l'annulation restaure, du plus récent au plus
 * vieux, et AVOUE ce qu'elle ne peut pas restaurer (une dépendance
 * ajoutée n'est pas défaisable ici). Plafond de 200 fichiers par
 * arbre : un projet entier ne se duplique pas en mémoire.
 */
public final class EspaceCheckpoint implements EspaceTravailAgent {

    /** Le plafond de fichiers instantanes par tour. */
    public static final int PLAFOND_FICHIERS = 200;

    /** Le rapport d'annulation : ce qui est restauré, ce qui ne l'est pas. */
    public static final class RapportAnnulation {
        /** Les fichiers restaurés (du plus récent au plus vieux). */
        public final List<String> restaures;
        /** Ce qui n'a PAS pu être restauré, avec sa raison. */
        public final List<String> nonRestaurables;

        /** @param restaures les fichiers restaurés @param nonRestaurables les actions non restaurables */
        public RapportAnnulation(List<String> restaures,
                                 List<String> nonRestaurables) {
            this.restaures = List.copyOf(restaures);
            this.nonRestaurables = List.copyOf(nonRestaurables);
        }

        /** @return vrai si l'annulation a quelque chose à raconter */
        public boolean aFaits() {
            return !restaures.isEmpty() || !nonRestaurables.isEmpty();
        }
    }

    private final EspaceTravailAgent dessous;
    private final Map<String, String> instantanes = new LinkedHashMap<>();
    private final List<String> nonRestaurables = new ArrayList<>();

    /** @param dessous l'espace de travail réel (le checkpoint enveloppe) */
    public EspaceCheckpoint(EspaceTravailAgent dessous) {
        this.dessous = dessous;
    }

    /** Le tour commence : les instantanes repartent à zéro. */
    public void commencerTour() {
        instantanes.clear();
        nonRestaurables.clear();
    }

    private void instantaner(String chemin) {
        if (instantanes.containsKey(chemin)
                || instantanes.size() >= PLAFOND_FICHIERS) {
            return;
        }
        try {
            instantanes.put(chemin, dessous.lireFichier(chemin));
        } catch (IOException e) {
            // le fichier n'existe pas encore : sa suppression sera
            // l'annulation (restaurer = supprimer), l'instantané vide
            instantanes.put(chemin, "");
        }
    }

    /**
     * Annule le tour : restaure les instantanes du plus récent au
     * plus vieux et RAPPORTE honnêtement ce qui ne se restaure pas.
     *
     * @return le rapport d'annulation
     */
    public RapportAnnulation annuler() {
        List<String> restaures = new ArrayList<>();
        List<String> chemins = new ArrayList<>(instantanes.keySet());
        for (int i = chemins.size() - 1; i >= 0; i--) {
            String chemin = chemins.get(i);
            String avant = instantanes.get(chemin);
            try {
                if (avant.isEmpty()) {
                    dessous.supprimerChemin(chemin);
                } else {
                    dessous.ecrireFichier(chemin, avant);
                }
                restaures.add(chemin);
            } catch (IOException e) {
                nonRestaurables.add(chemin + ": " + e.getMessage());
            }
        }
        return new RapportAnnulation(restaures, nonRestaurables);
    }

    /** Consigne une action non restaurable (une dépendance ajoutée, p. ex.). */
    public void nonRestaurable(String action) {
        nonRestaurables.add(action);
    }

    @Override
    public String lireFichier(String chemin) throws IOException {
        return dessous.lireFichier(chemin);
    }

    @Override
    public List<String> listerDossier(String chemin) throws IOException {
        return dessous.listerDossier(chemin);
    }

    @Override
    public List<OccurrenceEspace> chercherTexte(String motif,
                                                boolean expressionReguliere,
                                                int plafond)
            throws IOException {
        return dessous.chercherTexte(motif, expressionReguliere,
                plafond);
    }

    @Override
    public List<OccurrenceEspace> trouverSymbole(String nom) {
        return dessous.trouverSymbole(nom);
    }

    @Override
    public List<DiagnosticEspace> diagnostics(String chemin) {
        return dessous.diagnostics(chemin);
    }

    @Override
    public String apercuProjet() {
        return dessous.apercuProjet();
    }

    @Override
    public void ecrireFichier(String chemin, String contenu)
            throws IOException {
        instantaner(chemin);
        dessous.ecrireFichier(chemin, contenu);
    }

    @Override
    public void creerFichier(String chemin, String contenu)
            throws IOException {
        instantaner(chemin);
        dessous.creerFichier(chemin, contenu);
    }

    @Override
    public void editerFichier(String chemin, String ancien, String nouveau)
            throws IOException {
        instantaner(chemin);
        dessous.editerFichier(chemin, ancien, nouveau);
    }

    @Override
    public void renommerChemin(String de, String vers) throws IOException {
        instantaner(de);
        instantaner(vers);
        dessous.renommerChemin(de, vers);
    }

    @Override
    public void supprimerChemin(String chemin) throws IOException {
        instantaner(chemin);
        dessous.supprimerChemin(chemin);
    }

    @Override
    public boolean ajouterDependance(String module, String coordonnees) {
        boolean ajoute = dessous.ajouterDependance(module, coordonnees);
        if (ajoute) {
            // le lockfile et le cache ne se défont pas ici : le
            // rapport d'annulation le DIT, il ne le cache pas
            nonRestaurables.add("add dependency " + coordonnees
                    + " — resolver cache and lockfile are not "
                    + "reverted");
        }
        return ajoute;
    }

    @Override
    public java.util.Optional<PositionEspace> definition(String fichier,
                                                         int ligne,
                                                         int colonne) {
        return dessous.definition(fichier, ligne, colonne);
    }

    @Override
    public List<PositionEspace> references(String fichier, int ligne,
                                           int colonne) {
        return dessous.references(fichier, ligne, colonne);
    }

    @Override
    public void renommerSymbole(String fichier, int ligne, int colonne,
                                String nouveauNom) {
        dessous.renommerSymbole(fichier, ligne, colonne, nouveauNom);
    }

    @Override
    public String formaterFichier(String fichier) {
        return dessous.formaterFichier(fichier);
    }

    @Override
    public ResultatExecution executer(String cible, long delaiMaxSecondes) {
        return dessous.executer(cible, delaiMaxSecondes);
    }

    @Override
    public String lireMemoire() throws IOException {
        return dessous.lireMemoire();
    }

    @Override
    public void ecrireMemoire(String contenu) throws IOException {
        instantaner("AGENTS.md");
        dessous.ecrireMemoire(contenu);
    }

    @Override
    public String demanderUtilisateur(List<QuestionUtilisateur> questions) {
        return dessous.demanderUtilisateur(questions);
    }

    @Override
    public void close() {
        dessous.close();
    }

    /** Lit un fichier en octets UTF-8 (utilitaire interne des tests). */
    static String lire(Path chemin) throws IOException {
        return new String(Files.readAllBytes(chemin),
                StandardCharsets.UTF_8);
    }
}
