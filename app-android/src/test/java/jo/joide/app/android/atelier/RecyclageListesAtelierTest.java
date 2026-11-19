package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

import jo.joide.app.android.atelier.Arborescence.Noeud;

/**
 * ADR-085, lot A5 — le CŒUR du lot : le COÛT PAR MISE À JOUR des
 * listes de l'atelier est BORNÉ (constat I4). La MESURE : DiffUtil
 * calculé entre deux états consécutifs — une mise à jour ne re-lient
 * que les lignes CHANGÉES et ne gonfle que les lignes NOUVELLES ;
 * les lignes qui ne changent pas (0 « changed » ci-dessous) ne
 * traversent AUCUN gonflage ni re-lier — le coût d'une maj est une
 * fonction de la DIFF, jamais de la taille du plafond (400 lignes
 * conservées). C'est la propriété que le code d'hier (removeAllViews
 * + ré-inflation complète à chaque maj) violait.
 *
 * <p>La composition des éléments (ElementArbre, ElementProblemes) est
 * pure : ces tests la vérifient — plafond annoncé, racine masquée,
 * note de franchise, groupement par fichier, premier groupe sans
 * séparateur.</p>
 */
class RecyclageListesAtelierTest {

    /** Les opérations d'une mise à jour : [ajoutées, retirées, re-liées]. */
    private static int[] compter(List<?> avant, List<?> apres) {
        DiffUtil.DiffResult resultat = DiffUtil.calculateDiff(
                new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return avant.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return apres.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int ancien,
                                                    int nouveau) {
                        return avant.get(ancien)
                                .equals(apres.get(nouveau));
                    }

                    @Override
                    public boolean areContentsTheSame(int ancien,
                                                       int nouveau) {
                        return avant.get(ancien)
                                .equals(apres.get(nouveau));
                    }
                });
        int[] operations = new int[3];
        resultat.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int nombre) {
                operations[0] += nombre;
            }

            @Override
            public void onRemoved(int position, int nombre) {
                operations[1] += nombre;
            }

            @Override
            public void onChanged(int position, int nombre,
                                  Object charge) {
                operations[2] += nombre;
            }

            @Override
            public void onMoved(int depuis, int vers) {
                // un déplacement ne re-gonfle rien
            }
        });
        return operations;
    }

    @Test
    void leTranscriptDeLaConsoleNAjouteQueLaLigneNouvelle() {
        // un événement du démon P6 arrive : une ligne de plus, RIEN ne
        // se re-lier — même à 400 lignes, la maj ne coûte que la ligne
        jo.joide.app.android.console.EtatConsole etat =
                new jo.joide.app.android.console.EtatConsole();
        etat.surDemarrage(4, 0L);
        etat.surTache("app:compileKotlin", "SUCCEEDED", 100L);
        List<jo.joide.app.android.ConsoleBuild.Ligne> avant =
                etat.lignesFiltrees();
        etat.surTache("app:jar", "SUCCEEDED", 200L);
        List<jo.joide.app.android.ConsoleBuild.Ligne> apres =
                etat.lignesFiltrees();
        int[] operations = compter(avant, apres);
        assertEquals(2, avant.size(), "démarrage + une tâche");
        assertEquals(3, apres.size());
        assertEquals(1, operations[0], "une seule ligne gonflée");
        assertEquals(0, operations[1], "aucune ligne retirée");
        assertEquals(0, operations[2],
                "aucune ligne re-liée : le coût d'une maj est borné par "
                        + "la DIFF, pas par la taille du plafond");
    }

    @Test
    void leLogcatDeposeUnLotSansReLierLesLignesPrecedentes() {
        EtatLogcat etat = new EtatLogcat();
        etat.recevoir(EtatLogcat.Niveau.I, "Essai", "première", 1L);
        etat.recevoir(EtatLogcat.Niveau.D, "Essai", "deuxième", 2L);
        List<EtatLogcat.Ligne> avant = etat.visibles();
        etat.recevoir(EtatLogcat.Niveau.E, "Essai", "troisième", 3L);
        List<EtatLogcat.Ligne> apres = etat.visibles();
        int[] operations = compter(avant, apres);
        assertEquals(1, operations[0], "une seule ligne gonflée");
        assertEquals(0, operations[1]);
        assertEquals(0, operations[2],
                "le relais notifie à chaque lot : les lignes précédentes "
                        + "ne se re-lient pas");
    }

    @Test
    void leJournalAjouteSansReLierLesEvenementsPrecedents() {
        EtatJournal etat = new EtatJournal();
        etat.ajouter(EtatJournal.Source.INDEX,
                EtatJournal.Niveau.INFO, "premier");
        List<EtatJournal.Evenement> avant = etat.visibles();
        etat.ajouter(EtatJournal.Source.BUILD,
                EtatJournal.Niveau.INFO, "deuxième");
        List<EtatJournal.Evenement> apres = etat.visibles();
        int[] operations = compter(avant, apres);
        assertEquals(1, operations[0]);
        assertEquals(0, operations[1]);
        assertEquals(0, operations[2]);
    }

    @Test
    void replierUnDossierNeChangeAucuneLigneDeLArbre() {
        // l'identité d'une ligne de l'arbre est son CHEMIN : replier
        // retire le sous-arbre, tout le reste ne se re-lier pas
        List<Noeud> ouvert = Arborescence.composer(PROJET, "p",
                Set.of("app/"), null, false);
        List<Noeud> ferme = Arborescence.composer(PROJET, "p",
                Set.of(), null, false);
        List<ElementArbre> avant = ElementArbre.composer(ouvert, false,
                VueArbre.PLAFOND_NOEUDS);
        List<ElementArbre> apres = ElementArbre.composer(ferme, false,
                VueArbre.PLAFOND_NOEUDS);
        // les mêmes chemins restent : DiffUtil en mode identité-par-
        // valeur ne voit que les retraits du sous-arbre déplié
        int[] operations = compter(
                avant.stream().map(Object::toString).toList(),
                apres.stream().map(Object::toString).toList());
        assertTrue(operations[1] > 0, "le sous-arbre se retire");
        assertEquals(0, operations[2],
                "aucune ligne restante ne se re-lier : le dépliage est "
                        + "borné par la taille du sous-arbre, pas de "
                        + "l'arbre");
    }

    private static final List<String> PROJET = List.of(
            "app/src/main/java/jo/exemple/Outil.kt",
            "app/src/main/res/layout/main.xml",
            "joide.toml");

    @Test
    void laSectionProblemesNeRetoucheQueLeFichierReDiagnostique() {
        // le LSP re-diagnostique UN fichier : son groupe seul bouge,
        // les autres groupes ne se re-lient pas
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("src/A.java", "A.java", List.of(
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        1, "première", true),
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        2, "deuxième", false)));
        etat.poserFichier("src/B.java", "B.java", List.of(
                new EtatProblemes.Probleme("src/B.java", "B.java",
                        3, "b", true)));
        List<ElementProblemes> avant = ElementProblemes.composer(etat);
        etat.poserFichier("src/A.java", "A.java", List.of(
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        1, "première", true),
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        5, "cinquième", false),
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        7, "septième", true)));
        List<ElementProblemes> apres = ElementProblemes.composer(etat);
        // la sémantique de l'adaptateur : en-tête par fichier, ligne
        // par diagnostic (égalité de valeur)
        DiffUtil.ItemCallback<ElementProblemes> comparateur =
                comparateurProblemes();
        int[] operations = compterSemantique(avant, apres, comparateur);
        // reprise d'audit : re-diagnostiquer A le REPLACE EN QUEUE de
        // l'ordre d'arrivée (poserFichier : removeIf + addAll, l'identité
        // du lot L3) — les DEUX en-têtes se re-lient (le compte de A
        // ET le retournement des drapeaux « premier »), les groupes se
        // DÉPLACENT (un déplacement ne re-gonfle rien)
        assertEquals(2, operations[2],
                "deux en-têtes re-liés : le compte de A (2 → 3) et le "
                        + "retournement des deux drapeaux « premier »");
        assertTrue(operations[0] >= 2 && operations[1] >= 1,
                "les diagnostics de A se retirent et se gonflent : "
                        + operations[0] + " ajoutée(s), "
                        + operations[1] + " retirée(s)");
        // B ne bouge pas : sa ligne est identique avant et après
        ElementProblemes.Ligne ligneB = null;
        for (ElementProblemes element : apres) {
            if (element instanceof ElementProblemes.Ligne ligne
                    && ligne.probleme().nomFichier().equals("B.java")) {
                ligneB = ligne;
            }
        }
        assertTrue(avant.contains(ligneB),
                "la ligne de B est la MÊME valeur avant et après : elle "
                        + "ne se re-lier pas (coût borné au fichier "
                        + "re-diagnostiqué)");
    }

    @Test
    void reDiagnostiquerLeDernierFichierNCouteQueSonGroupe() {
        // reprise d'audit : le scénario où l'ordre d'arrivée est
        // STABLE (le DERNIER fichier re-diagnostiqué y reste) — un
        // SEUL en-tête se re-lier, le groupe intact (en-tête et
        // lignes) garde ses VALEURS : le coût d'une re-diagnostic LSP
        // est borné au groupe du fichier, jamais à la section
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("src/A.java", "A.java", List.of(
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        1, "première", true)));
        etat.poserFichier("src/B.java", "B.java", List.of(
                new EtatProblemes.Probleme("src/B.java", "B.java",
                        2, "b", true)));
        List<ElementProblemes> avant = ElementProblemes.composer(etat);
        etat.poserFichier("src/B.java", "B.java", List.of(
                new EtatProblemes.Probleme("src/B.java", "B.java",
                        2, "b", true),
                new EtatProblemes.Probleme("src/B.java", "B.java",
                        4, "d", false)));
        List<ElementProblemes> apres = ElementProblemes.composer(etat);
        int[] operations = compterSemantique(avant, apres,
                comparateurProblemes());
        assertEquals(1, operations[2],
                "seul l'en-tête de B se re-lier (le compte change : "
                        + "1 → 2)");
        assertEquals(1, operations[0], "la ligne d ajoutée");
        assertEquals(0, operations[1], "aucune ligne retirée");
        // le groupe A (en-tête ET lignes) garde ses valeurs exactes :
        // DiffUtil ne le re-lier pas
        ElementProblemes.Entete enteteA = (ElementProblemes.Entete)
                avant.get(0);
        ElementProblemes.Ligne ligneA = (ElementProblemes.Ligne)
                avant.get(1);
        assertTrue(apres.contains(enteteA),
                "l'en-tête de A garde sa valeur : jamais re-lié");
        assertTrue(apres.contains(ligneA),
                "la ligne de A garde sa valeur : jamais re-liée");
    }

    /** La sémantique de l'adaptateur problèmes (en-tête par fichier,
     *  ligne par diagnostic — égalité de valeur). */
    private static DiffUtil.ItemCallback<ElementProblemes>
    comparateurProblemes() {
        return new DiffUtil.ItemCallback<ElementProblemes>() {
            @Override
            public boolean areItemsTheSame(ElementProblemes ancien,
                                           ElementProblemes nouveau) {
                if (ancien instanceof ElementProblemes.Entete a
                        && nouveau
                        instanceof ElementProblemes.Entete b) {
                    return a.fichier().equals(b.fichier());
                }
                return ancien.equals(nouveau);
            }

            @Override
            public boolean areContentsTheSame(
                    ElementProblemes ancien,
                    ElementProblemes nouveau) {
                return ancien.equals(nouveau);
            }
        };
    }

    @Test
    void laCompositionDeLArbreMasqueLaRacineEtPorteLaNoteDeSorties() {
        List<Noeud> noeuds = Arborescence.composer(PROJET, "p",
                Set.of(), null, true);
        List<ElementArbre> elements = ElementArbre.composer(noeuds,
                false, VueArbre.PLAFOND_NOEUDS);
        assertFalse(elements.isEmpty());
        assertTrue(elements.stream().noneMatch(e ->
                        e instanceof ElementArbre.Ligne ligne
                                && ligne.noeud().profondeur() == 0),
                "la racine (profondeur 0) ne s'affiche pas : l'en-tête "
                        + "porte le nom du projet");
        assertFalse(elements.contains(
                        new ElementArbre.Tronque(VueArbre.PLAFOND_NOEUDS)),
                "sous le plafond, aucune troncature");
        assertTrue(elements.stream().anyMatch(e ->
                        e instanceof ElementArbre.NoteSorties),
                "la note de franchise suit le nœud des sorties");
    }

    @Test
    void laCompositionDeLArbrePlafonneEtSeDit() {
        // 3 fichiers + dossiers : un plafond de 2 lignes coupe avec sa
        // raison — jamais un affichage rendu muet
        List<Noeud> noeuds = Arborescence.composer(PROJET, "p",
                Set.of("app/", "app/src/", "app/src/main/"), null,
                false);
        List<ElementArbre> elements = ElementArbre.composer(noeuds,
                false, 2);
        assertEquals(2, elements.stream().filter(e ->
                        e instanceof ElementArbre.Ligne).count(),
                "deux lignes montrées");
        ElementArbre.Tronque tronque = null;
        for (ElementArbre element : elements) {
            if (element instanceof ElementArbre.Tronque note) {
                tronque = note;
            }
        }
        assertEquals(new ElementArbre.Tronque(2), tronque,
                "la troncature porte le plafond : la note le DIT");
        // la note de franchise suit le nœud des sorties
        List<Noeud> avecSorties = Arborescence.composer(PROJET, "p",
                Set.of(), null, true);
        List<ElementArbre> complet = ElementArbre.composer(avecSorties,
                true, VueArbre.PLAFOND_NOEUDS);
        assertTrue(complet.stream().anyMatch(e ->
                        e instanceof ElementArbre.NoteSorties note
                                && note.enCours()),
                "la note de franchise suit le nœud des sorties, "
                        + "atténuée pendant le build");
    }

    @Test
    void lesProblemesSeGroupentLePremierSansSeparateur() {
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("src/A.java", "A.java", List.of(
                new EtatProblemes.Probleme("src/A.java", "A.java",
                        1, "a", true)));
        etat.poserFichier("src/B.java", "B.java", List.of(
                new EtatProblemes.Probleme("src/B.java", "B.java",
                        2, "b", false)));
        List<ElementProblemes> elements = ElementProblemes.composer(etat);
        assertEquals(4, elements.size(),
                "deux en-têtes, deux lignes");
        ElementProblemes.Entete premier = (ElementProblemes.Entete)
                elements.get(0);
        ElementProblemes.Entete deuxieme = (ElementProblemes.Entete)
                elements.get(2);
        assertTrue(premier.premier(), "le premier groupe n'a pas de séparateur");
        assertFalse(deuxieme.premier());
        assertEquals("A.java", premier.nomAffiche());
        assertEquals(1, premier.nombre());
        assertEquals("src/B.java", deuxieme.fichier());
    }

    /** Les opérations d'une maj avec la sémantique de l'adaptateur. */
    private static <E> int[] compterSemantique(List<E> avant, List<E> apres,
                                               DiffUtil.ItemCallback<E> comparateur) {
        DiffUtil.DiffResult resultat = DiffUtil.calculateDiff(
                new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return avant.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return apres.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int ancien,
                                                    int nouveau) {
                        return comparateur.areItemsTheSame(
                                avant.get(ancien), apres.get(nouveau));
                    }

                    @Override
                    public boolean areContentsTheSame(int ancien,
                                                       int nouveau) {
                        return comparateur.areContentsTheSame(
                                avant.get(ancien), apres.get(nouveau));
                    }
                });
        int[] operations = new int[3];
        resultat.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int nombre) {
                operations[0] += nombre;
            }

            @Override
            public void onRemoved(int position, int nombre) {
                operations[1] += nombre;
            }

            @Override
            public void onChanged(int position, int nombre,
                                  Object charge) {
                operations[2] += nombre;
            }

            @Override
            public void onMoved(int depuis, int vers) {
                // un déplacement ne re-gonfle rien
            }
        });
        return operations;
    }
}
