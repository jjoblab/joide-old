package jo.joide.agent.espace;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Le délégataire de l'espace de travail (lot P) : enveloppe un espace
 * et redélègue TOUT, méthode par méthode — l'hôte (le service de
 * l'agent) y surcharge uniquement ce qu'il intercepte (les questions
 * {@code ask_user}, typiquement). Jamais une sur-classe qui oublierait
 * une méthode nouvelle : chaque méthode redélègue EXPLICITEMENT, une
 * signature ajoutée au port casse la compilation ici — le compilateur
 * fait la revue.
 */
public class EspaceTravailInjecteur implements EspaceTravailAgent {

    private final EspaceTravailAgent dessous;

    /**
     * @param dessous l'espace délégué
     */
    public EspaceTravailInjecteur(EspaceTravailAgent dessous) {
        this.dessous = dessous;
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
        dessous.ecrireFichier(chemin, contenu);
    }

    @Override
    public void creerFichier(String chemin, String contenu)
            throws IOException {
        dessous.creerFichier(chemin, contenu);
    }

    @Override
    public void editerFichier(String chemin, String ancien, String nouveau)
            throws IOException {
        dessous.editerFichier(chemin, ancien, nouveau);
    }

    @Override
    public void renommerChemin(String de, String vers) throws IOException {
        dessous.renommerChemin(de, vers);
    }

    @Override
    public void supprimerChemin(String chemin) throws IOException {
        dessous.supprimerChemin(chemin);
    }

    @Override
    public boolean ajouterDependance(String module, String coordonnees) {
        return dessous.ajouterDependance(module, coordonnees);
    }

    @Override
    public Optional<PositionEspace> definition(String fichier, int ligne,
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
}
