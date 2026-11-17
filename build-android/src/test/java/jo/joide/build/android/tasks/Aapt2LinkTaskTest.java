package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.Problem;
import jo.joide.platform.Severity;

/**
 * Le manifeste enrichie (lot XVII, le terrain du 2026-11-17 :
 * « &lt;manifest&gt; must have a 'package' attribute ») : depuis AGP 7
 * le package vit dans {@code namespace}, plus dans le manifeste —
 * aapt2 (couche basse) exige pourtant l'attribut, la manière AGP est
 * de le synthétiser dans le manifeste mergé. La copie s'écrit dans
 * $OUT (JAMAIS dans les sources), la chaîne de secours est
 * manifeste → identifiant d'application → dérivation du nom, et une
 * synthèse émet un WARN qui nomme le correctif recommandé.
 */
class Aapt2LinkTaskTest {

    @Test
    void lePaquetSeSynthetiseDepuisLApplicationId(@TempDir Path dossier)
            throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        Files.writeString(source, "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com"
                + "/apk/res/android\">\n"
                + "    <application android:label=\"@string/app_name\" />\n"
                + "</manifest>\n");
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Path ecrit = Aapt2LinkTask.enrichir(source, cible,
                "jo.exemple.monapplication", ":app", notes);
        assertEquals(cible, ecrit, "le manifeste enrichie s'écrit à la cible");
        String texte = Files.readString(cible);
        assertTrue(texte.contains(
                        "<manifest package=\"jo.exemple.monapplication\""),
                "le paquet se synthétise EN PREMIER attribut : " + texte);
        assertTrue(texte.contains(
                        "xmlns:android=\"http://schemas.android.com/apk/res/android\""),
                "les attributs existants restent : " + texte);
        assertTrue(texte.contains("<application android:label="),
                "le corps du manifeste reste intact : " + texte);
        assertEquals(1, notes.size(), "une synthèse émet UNE note");
        assertEquals(Severity.WARN, notes.get(0).severity(),
                "la synthèse prévient (le correctif recommandé reste "
                        + "d'écrire le paquet dans le manifeste)");
        assertTrue(notes.get(0).message().contains(
                        "jo.exemple.monapplication"),
                "la note porte le paquet synthétisé : "
                        + notes.get(0).message());
    }

    @Test
    void lePaquetSeDeriveDuNomEnDernierRecours(@TempDir Path dossier)
            throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        Files.writeString(source,
                "<manifest xmlns:android=\"http://schemas.android.com"
                        + "/apk/res/android\" />");
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Aapt2LinkTask.enrichir(source, cible, null, ":core", notes);
        assertTrue(Files.readString(cible).contains(
                        "<manifest package=\"joide.app._core\""),
                "sans variante, la dérivation du nom tient — EXACTEMENT "
                        + "la règle d'identifiantPaquet (le souligné du "
                        + "« : » survit) : " + Files.readString(cible));
    }

    @Test
    void lePaquetPresentResteInchange(@TempDir Path dossier)
            throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        String manifeste = "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com"
                + "/apk/res/android\" package=\"jo.exemple.monapplication\">\n"
                + "</manifest>\n";
        Files.writeString(source, manifeste);
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Aapt2LinkTask.enrichir(source, cible, "autre.id", ":app", notes);
        assertEquals(manifeste, Files.readString(cible),
                "un manifeste avec paquet est copié SANS modification");
        assertTrue(notes.isEmpty(), "aucune note : rien n'est synthétisé");
    }

    @Test
    void laBaliseSurPlusieursLignesSeLit(@TempDir Path dossier)
            throws IOException {
        assertEquals("jo.exemple.monapplication",
                Aapt2LinkTask.paquetDuManifeste(
                        "<manifest\n    xmlns:android=\"http://a\"\n"
                                + "    package=\"jo.exemple.monapplication\">"),
                "une balise d'ouverture multiligne se lit");
        assertNull(Aapt2LinkTask.paquetDuManifeste(
                        "<manifest xmlns:android=\"http://a\">"),
                "sans attribut, null (la synthèse prend le relais)");
    }

    @Test
    void laDerivationReproduitIdentifiantPaquet() {
        // EXACTEMENT la règle d'identifiantPaquet (SpecsBuild) :
        // « : » devient « _ » AVANT le nettoyage — le souligné survit
        assertEquals("joide.app._core",
                Aapt2LinkTask.paquetDerive(":core"), "la règle du nom");
        assertEquals("joide.app._app",
                Aapt2LinkTask.paquetDerive(":app"), "le module d'app");
        assertEquals("joide.app._monapplication",
                Aapt2LinkTask.paquetDerive(":mon-application"),
                "les séparateurs du nom disparaissent");
    }
}
