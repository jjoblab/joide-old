package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.platform.Problem;
import jo.joide.platform.Severity;

/**
 * Le manifeste enrichie (lots XVII et C10) : depuis AGP 7 le paquet
 * vit dans {@code namespace} et le code de version dans le
 * {@code build.gradle} (la variante), plus dans le manifeste — aapt2
 * (couche basse) exige pourtant le paquet, et bundletool exige le
 * code de version du module de base (le terrain du 2026-11-20 : «
 * bundle assembly failed: Version code not found in manifest »).
 * La manière AGP : tout se synthétise dans le manifeste MERGÉ. La
 * copie s'écrit dans $OUT (JAMAIS dans les sources), la chaîne de
 * secours est manifeste → identifiant d'application → dérivation du
 * nom, la version vient de la variante (un module bibliothèque
 * n'en porte pas), et une synthèse émet un WARN qui nomme le
 * correctif recommandé.
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
                "jo.exemple.monapplication", ":app", null, notes);
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
        Aapt2LinkTask.enrichir(source, cible, null, ":core", null, notes);
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
        Aapt2LinkTask.enrichir(source, cible, "autre.id", ":app", null,
                notes);
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

    /** La variante du terrain : gabarit AGP — paquet présent, NI
     *  versionCode NI versionName NI uses-sdk. */
    private static AndroidVariant varianteDuTerrain() {
        return AndroidVariant.builder("jo.exemple.monapplication", "debug")
                .versionCode(7)
                .versionName("1.7")
                .minSdk(23)
                .targetSdk(35)
                .build();
    }

    @Test
    void leCodeDeVersionSeSynthetisePourBundletool(
            @TempDir Path dossier) throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        Files.writeString(source, "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com"
                + "/apk/res/android\"\n"
                + "    package=\"jo.exemple.monapplication\">\n"
                + "    <application android:label=\"@string/app_name\" />\n"
                + "</manifest>\n");
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Aapt2LinkTask.enrichir(source, cible,
                "jo.exemple.monapplication", ":app",
                varianteDuTerrain(), notes);
        String texte = Files.readString(cible);
        assertTrue(texte.contains("android:versionCode=\"7\""),
                "le code de version se synthétise de la variante : "
                        + texte);
        assertTrue(texte.contains("android:versionName=\"1.7\""),
                "le nom de version se synthétise de la variante : "
                        + texte);
        assertTrue(texte.contains("<uses-sdk"),
                "l'élément uses-sdk se synthétise (le dex est déjà taillé "
                        + "à minSdk) : " + texte);
        assertTrue(texte.contains("android:minSdkVersion=\"23\""),
                "le SDK minimum vient de la variante : " + texte);
        assertTrue(texte.contains("android:targetSdkVersion=\"35\""),
                "le SDK cible vient de la variante : " + texte);
        assertTrue(texte.contains("<application android:label="),
                "le corps du manifeste reste intact : " + texte);
        assertEquals(3, notes.size(), "versionCode, versionName et uses-sdk "
                + "synthétisés : TROIS notes");
        assertTrue(notes.stream().allMatch(
                        p -> p.severity() == Severity.WARN),
                "chaque synthèse prévient");
        assertTrue(notes.stream().anyMatch(p -> p.message().contains(
                        "android:versionCode synthétisé (« 7 »")),
                "la note du code de version nomme le correctif : " + notes);
    }

    @Test
    void leManifesteCompletResteInchange(@TempDir Path dossier)
            throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        String manifeste = "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com"
                + "/apk/res/android\"\n"
                + "    package=\"jo.exemple.monapplication\"\n"
                + "    android:versionCode=\"9\"\n"
                + "    android:versionName=\"9.0\">\n"
                + "  <uses-sdk android:minSdkVersion=\"21\""
                + " android:targetSdkVersion=\"33\" />\n"
                + "</manifest>\n";
        Files.writeString(source, manifeste);
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Aapt2LinkTask.enrichir(source, cible,
                "jo.exemple.monapplication", ":app",
                varianteDuTerrain(), notes);
        assertEquals(manifeste, Files.readString(cible),
                "un manifeste qui porte déjà paquet, versions ET uses-sdk "
                        + "est copié SANS modification");
        assertTrue(notes.isEmpty(), "aucune note : rien n'est synthétisé");
    }

    @Test
    void laBaliseAutoFermeePorteSaVersionSansEnfant(
            @TempDir Path dossier) throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        Files.writeString(source, "<manifest xmlns:android=\"http://a\" "
                + "package=\"jo.exemple.monapplication\" />");
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Aapt2LinkTask.enrichir(source, cible,
                "jo.exemple.monapplication", ":app",
                varianteDuTerrain(), notes);
        String texte = Files.readString(cible);
        assertTrue(texte.contains("android:versionCode=\"7\""),
                "le code de version se synthétise : " + texte);
        assertTrue(texte.contains("android:versionName=\"1.7\"/>"),
                "l'auto-fermeture reste valide (dernier attribut "
                        + "suivi de />) : " + texte);
        assertTrue(texte.endsWith("/>"),
                "la balise reste auto-fermée : " + texte);
        assertFalse(texte.contains("<uses-sdk"),
                "un manifeste auto-fermé ne peut pas porter d'enfant : "
                        + texte);
        assertEquals(2, notes.size(), "versionCode et versionName, pas "
                + "d'uses-sdk : DEUX notes");
    }

    @Test
    void sansVarianteAucuneVersionNEcrit(@TempDir Path dossier)
            throws IOException {
        Path source = dossier.resolve("AndroidManifest.xml");
        String manifeste = "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com"
                + "/apk/res/android\" package=\"jo.exemple.lib\">\n"
                + "</manifest>\n";
        Files.writeString(source, manifeste);
        Path cible = dossier.resolve("out").resolve("manifest-enrichi.xml");
        List<Problem> notes = new ArrayList<>();
        Aapt2LinkTask.enrichir(source, cible, "jo.exemple.lib", ":lib",
                null, notes);
        assertEquals(manifeste, Files.readString(cible),
                "un module bibliothèque n'est pas porteur de version : "
                        + "le manifeste ne change pas");
        assertTrue(notes.isEmpty(), "aucune note");
    }
}
