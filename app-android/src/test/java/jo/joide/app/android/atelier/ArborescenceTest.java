package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.Arborescence.Noeud;

/**
 * L'arbre du tiroir (lot J, ADR-É5) : dossiers avant fichiers,
 * entrée active surlignée, sorties estompées en fin de racine —
 * « jamais un nœud build-joide présenté comme source ».
 */
class ArborescenceTest {

    private static final List<String> PROJET = List.of(
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/jo/exemple/Outil.kt",
            "app/src/main/res/layout/main.xml",
            "app/src/main/res/values/strings.xml",
            "core/src/main/java/Outil.java",
            "joide.toml");

    @Test
    void racineEtTrieDossiersAvantFichiers() {
        List<Noeud> lignes = Arborescence.composer(PROJET,
                "mon-application", Set.of("app/", "app/src/"), null,
                false);
        assertEquals("mon-application", lignes.get(0).nom());
        assertEquals(0, lignes.get(0).profondeur());
        assertTrue(lignes.get(0).dossier());
        // à la racine : le dossier app AVANT le fichier joide.toml
        assertEquals("app", lignes.get(1).nom());
        assertTrue(lignes.get(1).dossier());
        assertEquals("joide.toml",
                lignes.get(lignes.size() - 1).nom());
        assertFalse(lignes.get(lignes.size() - 1).dossier());
    }

    @Test
    void dossierFermeCacheSesEnfants() {
        List<Noeud> ferme = Arborescence.composer(PROJET,
                "p", Set.of(), null, false);
        List<Noeud> ouvert = Arborescence.composer(PROJET,
                "p", Set.of("app/"), null, false);
        assertTrue(ferme.size() < ouvert.size(),
                "déplier app révèle ses enfants");
        assertTrue(ouvert.stream().anyMatch(n ->
                "src".equals(n.nom()) && n.chemin().equals("app/src/")));
        assertTrue(ferme.stream().noneMatch(n ->
                        !n.chemin().equals("app/")
                                && n.chemin().startsWith("app/")),
                "fermé : aucun enfant visible, seulement le nœud app");
    }

    @Test
    void leMemeNomFichierEtDossierResteUnDossier() {
        List<Noeud> lignes = Arborescence.composer(
                List.of("a/b.txt", "a"), "p", Set.of("a/"), null, false);
        Noeud a = lignes.stream().filter(n -> "a".equals(n.nom()))
                .findFirst().orElseThrow();
        assertTrue(a.dossier(),
                "« a » est traversé par a/b.txt : c'est un dossier");
    }

    @Test
    void entreeActiveSurligneeParmiSesPairs() {
        List<Noeud> lignes = Arborescence.composer(PROJET, "p",
                Arborescence.parentsDe(
                        "app/src/main/res/layout/main.xml"),
                "app/src/main/res/layout/main.xml", false);
        long actifs = lignes.stream().filter(Noeud::actif).count();
        assertEquals(1, actifs, "exactement une entrée active");
        Noeud actif = lignes.stream().filter(Noeud::actif).findFirst()
                .orElseThrow();
        assertEquals("main.xml", actif.nom());
        assertEquals(6, actif.profondeur(),
                "racine 0, app 1, src 2, main 3, res 4, layout 5, fichier 6");
        assertFalse(actif.dossier());
    }

    @Test
    void sortiesDeBuildEnTouneFinDeRacineJamaisCommeSource() {
        List<Noeud> lignes = Arborescence.composer(PROJET, "p",
                Set.of(), null, true);
        Noeud dernier = lignes.get(lignes.size() - 1);
        assertEquals(Arborescence.NOM_SORTIES, dernier.nom());
        assertTrue(dernier.sorties(),
                "le nœud des sorties porte sa marque");
        assertTrue(dernier.dossier());
        assertFalse(dernier.ouvert(),
                "les sorties ne se déplient pas : pas des sources");
        assertEquals(1, dernier.profondeur());
        // absentes : le nœud disparaît, il n'est jamais inventé
        List<Noeud> sans = Arborescence.composer(PROJET, "p",
                Set.of(), null, false);
        assertTrue(sans.stream().noneMatch(Noeud::sorties));
    }

    @Test
    void parentsDeReveleUnCheminProfond() {
        Set<String> parents = Arborescence.parentsDe(
                "app/src/main/res/layout/main.xml");
        assertEquals(Set.of("app/", "app/src/", "app/src/main/",
                "app/src/main/res/", "app/src/main/res/layout/"),
                parents);
        assertTrue(Arborescence.parentsDe(null).isEmpty());
        assertTrue(Arborescence.parentsDe("joide.toml").isEmpty());
    }

    @Test
    void dossierEtNomAffiche() {
        assertEquals("", Arborescence.dossierDe("joide.toml"));
        assertEquals("app/src/main/",
                Arborescence.dossierDe("app/src/main/A.java"));
        assertEquals("A.java", Arborescence.nomAffiche(
                "app/src/main/A.java"));
        assertEquals("joide.toml", Arborescence.nomAffiche("joide.toml"));
    }
}
