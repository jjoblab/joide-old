package jo.joide.app.android.ui.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

import jo.joide.app.android.atelier.ModeleOnglets;

/**
 * ADR-085, lot A6 — l'onglet TEL QUE L'ADAPTATEUR le voit
 * ({@code OngletVu}) : la VALEUR DE RENDU de la bande d'onglets
 * recyclée. Deux propriétés testées : l'IDENTITÉ
 * ({@code memeOnglet} : le CHEMIN — l'onglet se déplace ou
 * disparaît, il ne se re-lier jamais de zéro) et le RENDU
 * ({@code memeRendu} : drapeau actif, badge de type, points
 * d'état, nom affiché — tout ce que la reliature RE-POSE, les vues
 * se recyclent). Et la MESURE DiffUtil entre deux états consécutifs
 * borne le coût : activer un autre onglet ne RE-LIE que les DEUX
 * onglets concernés, jamais la bande entière — l'identité
 * {@code removeAllViews()}/ré-inflation complète d'hier
 * (constat I4, signalé au rapport A5 pour la bande) est morte.
 */
class OngletVuTest {

    /** Les opérations d'une maj : [ajoutées, retirées, re-liées]. */
    private static int[] compter(List<OngletVu> avant,
                                  List<OngletVu> apres) {
        int[] compte = {0, 0, 0};
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
                                                    int neuf) {
                        return avant.get(ancien)
                                .memeOnglet(apres.get(neuf));
                    }

                    @Override
                    public boolean areContentsTheSame(int ancien,
                                                       int neuf) {
                        return avant.get(ancien)
                                .memeRendu(apres.get(neuf));
                    }
                }, false);
        resultat.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int nombre) {
                compte[0] += nombre;
            }

            @Override
            public void onRemoved(int position, int nombre) {
                compte[1] += nombre;
            }

            @Override
            public void onMoved(int de, int vers) {
                // la bande n'est jamais réordonnée par activation
            }

            @Override
            public void onChanged(int position, int nombre,
                                  Object charge) {
                compte[2] += nombre;
            }
        });
        return compte;
    }

    /** Un modèle de trois onglets ouverts, l'actif donné. */
    private static ModeleOnglets modele(String actif) {
        ModeleOnglets modele = new ModeleOnglets();
        modele.ouvrir("src/Alpha.java", "Alpha.java", false, false);
        modele.ouvrir("res/layout/b.xml", "b.xml", true, false);
        modele.ouvrir("build.gradle.kts", "build.gradle.kts", false,
                true);
        if (actif != null) {
            modele.activer(actif);
        }
        return modele;
    }

    @Test
    void lActifPorteLeDrapeauEtLuiSeul() {
        List<OngletVu> vus = OngletVu.depuis(modele("res/layout/b.xml"));
        assertEquals(3, vus.size());
        assertFalse(vus.get(0).actif(), "le premier est inactif");
        assertTrue(vus.get(1).actif(), "l'actif porte le drapeau");
        assertFalse(vus.get(2).actif(), "le dernier est inactif");
    }

    @Test
    void lesOngletsRestentDansLOrdreDuModele() {
        // l'ordre porté au RecyclerView EST l'ordre du modèle : le
        // défilement minimal vers l'actif (indexActif) compte dessus
        List<OngletVu> vus = OngletVu.depuis(modele("src/Alpha.java"));
        assertEquals("src/Alpha.java", vus.get(0).onglet().chemin);
        assertEquals("res/layout/b.xml", vus.get(1).onglet().chemin);
        assertEquals("build.gradle.kts", vus.get(2).onglet().chemin);
        // activer() un chemin ABSENT est sans effet : l'actif réel
        // reste Alpha — le drapeau suit l'état, jamais l'appel
        ModeleOnglets modele = modele("src/Alpha.java");
        modele.activer("chemin/absent.kt");
        List<OngletVu> tentes = OngletVu.depuis(modele);
        assertTrue(tentes.get(0).actif(),
                "l'actif réel garde son drapeau");
        assertFalse(tentes.get(1).actif());
        assertFalse(tentes.get(2).actif());
    }

    @Test
    void lIdentiteEstLeCheminMemeSiLEtatChange() {
        OngletVu avant = new OngletVu(new ModeleOnglets.EtatOnglet(
                "src/Alpha.java", "Alpha.java",
                ModeleOnglets.Badge.JAVA, false, false), true);
        // le même fichier, coché modifié : le badge ne bouge pas
        // (ouvrir() le garde), le point d'état si
        OngletVu apres = new OngletVu(new ModeleOnglets.EtatOnglet(
                "src/Alpha.java", "Alpha.java",
                ModeleOnglets.Badge.JAVA, true, false), true);
        assertTrue(avant.memeOnglet(apres),
                "le même fichier ouvert reste le même onglet");
        assertFalse(avant.memeRendu(apres),
                "le point « modifié » est un changement de rendu");

        OngletVu autre = new OngletVu(new ModeleOnglets.EtatOnglet(
                "src/Beta.java", "Beta.java",
                ModeleOnglets.Badge.JAVA, false, false), true);
        assertFalse(avant.memeOnglet(autre),
                "un autre fichier est un autre onglet");
    }

    @Test
    void leRenduCompareToutCeQueLaReliatureRepose() {
        ModeleOnglets.EtatOnglet base = new ModeleOnglets.EtatOnglet(
                "a.xml", "a.xml", ModeleOnglets.Badge.XML, false,
                false);
        assertTrue(new OngletVu(base, true).memeRendu(
                new OngletVu(base, true)),
                "deux rendus identiques");
        // le drapeau actif (l'activation re-lier l'ancien ET le neuf)
        assertFalse(new OngletVu(base, true).memeRendu(
                new OngletVu(base, false)),
                "le drapeau actif décide du rendu");
        // le badge de type (la pastille se teinte au bind)
        assertFalse(new OngletVu(base, true).memeRendu(new OngletVu(
                new ModeleOnglets.EtatOnglet("a.xml", "a.xml",
                        ModeleOnglets.Badge.CONFIG, false, false),
                true)), "le badge décide du rendu");
        // le point « modifié »
        assertFalse(new OngletVu(base, true).memeRendu(new OngletVu(
                new ModeleOnglets.EtatOnglet("a.xml", "a.xml",
                        ModeleOnglets.Badge.XML, true, false), true)),
                "le point modifié décide du rendu");
        // le point d'erreur
        assertFalse(new OngletVu(base, true).memeRendu(new OngletVu(
                new ModeleOnglets.EtatOnglet("a.xml", "a.xml",
                        ModeleOnglets.Badge.XML, false, true), true)),
                "le point erreur décide du rendu");
        // le nom affiché (l'onglet le montre)
        assertFalse(new OngletVu(base, true).memeRendu(new OngletVu(
                new ModeleOnglets.EtatOnglet("a.xml", "a (2).xml",
                        ModeleOnglets.Badge.XML, false, false), true)),
                "le nom affiché décide du rendu");
    }

    @Test
    void activerUnAutreOngletNeReLieQueLesDeuxConcernes() {
        List<OngletVu> avant = OngletVu.depuis(
                modele("src/Alpha.java"));
        List<OngletVu> apres = OngletVu.depuis(
                modele("res/layout/b.xml"));
        int[] compte = compter(avant, apres);
        assertEquals(0, compte[0], "aucun onglet gonflé");
        assertEquals(0, compte[1], "aucun onglet retiré");
        assertEquals(2, compte[2],
                "seuls l'ancien actif et le neuf se re-lient — "
                        + "jamais la bande entière (le coût d'une "
                        + "activation est borné)");
    }

    @Test
    void ouvrirUnOngletNEstQuUneInsertion() {
        ModeleOnglets modele = modele("src/Alpha.java");
        List<OngletVu> avant = OngletVu.depuis(modele);
        modele.ouvrir("src/Gamma.kt", "Gamma.kt", false, false);
        List<OngletVu> apres = OngletVu.depuis(modele);
        int[] compte = compter(avant, apres);
        assertEquals(1, compte[0], "le nouvel onglet seul gonfle");
        assertEquals(0, compte[1], "rien n'est retiré");
        assertEquals(1, compte[2], "l'ancien actif seul se re-lier "
                + "(ouvrir ACTIVE le neuf : le drapeau de l'ancien "
                + "tombe) — le tiers restant ne traverse aucun "
                + "gonflage ni re-lier");
    }

    @Test
    void fermerUnOngletNEstQuUnRetrait() {
        ModeleOnglets modele = modele("res/layout/b.xml");
        List<OngletVu> avant = OngletVu.depuis(modele);
        modele.fermer("res/layout/b.xml");
        List<OngletVu> apres = OngletVu.depuis(modele);
        int[] compte = compter(avant, apres);
        assertEquals(0, compte[0], "rien ne gonfle");
        assertEquals(1, compte[1], "l'onglet fermé seul disparaît");
        assertEquals(1, compte[2],
                "le voisin devenu actif se re-lier (drapeau)");
    }
}
