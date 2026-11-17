package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.build.jvm.worker.KotlinWorkerMain;

/**
 * Essai BOUT-EN-BOUT du pilote par VM forkée (lot VII) : le vrai
 * ouvrier KotlinWorkerMain lancé en sous-processus {@code java} —
 * le MÊME chemin que sur l'appareil, où le binaire devient
 * {@code dalvikvm64} et le classpath le pack de dex différé. Les
 * surcharges système {@code joide.kotlinVm} et
 * {@code joide.kotlinWorkerCp} branchent l'essai sans toucher au
 * pack (aucun téléchargement ici).
 */
class KotlinForkedDriverTest {

    @TempDir
    static Path dossier;

    private static String ancienneVm;
    private static String ancienCp;

    @BeforeAll
    static void brancher() throws Exception {
        ancienneVm = System.setProperty("joide.kotlinVm",
                Path.of(System.getProperty("java.home"), "bin", "java")
                        .toString());
        ancienCp = System.setProperty("joide.kotlinWorkerCp",
                classpathOuvrier());
    }

    @AfterAll
    static void debrancher() {
        if (ancienneVm != null) {
            System.setProperty("joide.kotlinVm", ancienneVm);
        } else {
            System.clearProperty("joide.kotlinVm");
        }
        if (ancienCp != null) {
            System.setProperty("joide.kotlinWorkerCp", ancienCp);
        } else {
            System.clearProperty("joide.kotlinWorkerCp");
        }
    }

    /**
     * Un lot sain écrit ses classes, l'ouvrière SURVIT et la MÊME VM
     * sert le lot suivant (un démarrage de compilateur ne se paie
     * qu'une fois).
     */
    @Test
    void compileEtReutiliseLOuvriere() throws Exception {
        Path sortie = dossier.resolve("sortie-saine");
        Files.createDirectories(sortie);
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun salut(): Int = 40 + 2\n");
        KotlinForkedDriver pilote = new KotlinForkedDriver();
        CompileResult premier = pilote.compile(requete(source, sortie));
        assertTrue(premier.success,
                () -> "échec inattendu : " + premier.diagnostics);
        assertTrue(Files.isRegularFile(sortie.resolve("demo/SalutKt.class")),
                "la classe écrite manque : " + premier.writtenClasses);
        assertTrue(premier.writtenClasses.contains("demo/SalutKt.class"));
        assertTrue(pilote.ouvriereVivante(),
                "l'ouvrière devrait survivre au lot");

        // le second lot appelle le PREMIER (classpath = stdlib +
        // sortie) : la même ouvrière le sert — preuve de réutilisation
        Path second = dossier.resolve("Salut2.kt");
        Files.writeString(second, "package demo\n\n"
                + "fun salut2(): Int = salut() + 1\n");
        CompileResult deuxieme = pilote.compile(new CompileRequest(
                List.of(second),
                List.of(jarDe(kotlin.KotlinVersion.class), sortie),
                List.of(), sortie, "17", "UTF-8", false));
        assertTrue(deuxieme.success,
                () -> "échec inattendu : " + deuxieme.diagnostics);
        assertTrue(Files.isRegularFile(sortie.resolve("demo/Salut2Kt.class")),
                "la classe écrite manque : " + deuxieme.writtenClasses);
        assertTrue(pilote.ouvriereVivante());
    }

    /** Un lot fautif échoue et porte sa localisation (chemin:ligne). */
    @Test
    void lErreurPorteSaLocalisation() throws Exception {
        Path sortie = dossier.resolve("sortie-fautive");
        Files.createDirectories(sortie);
        Path source = dossier.resolve("Fautif.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun fautif(): Int = inconnu\n");
        KotlinForkedDriver pilote = new KotlinForkedDriver();
        CompileResult resultat = pilote.compile(requete(source, sortie));
        assertFalse(resultat.success, "le lot fautif doit échouer");
        assertFalse(resultat.diagnostics.isEmpty(),
                "le lot fautif doit diagnostiquer");
        assertNotNull(resultat.diagnostics.get(0).location(),
                "le diagnostic doit être localisé");
        assertTrue(resultat.diagnostics.get(0).location()
                        .contains("Fautif.kt:3:"),
                "localisation inattendue : "
                        + resultat.diagnostics.get(0).location());
        assertTrue(resultat.diagnostics.stream()
                        .anyMatch(p -> p.isError() && "kotlin.error"
                                .equals(p.code())),
                "au moins une erreur kotlin.error : "
                        + resultat.diagnostics);
    }

    /** Le pilote s'identifie par la version du pack (outils chauds). */
    @Test
    void laVersionPorteLeSuffixeForke() {
        CompilerDriver pilote = new KotlinForkedDriver();
        assertTrue(pilote.toolVersion()
                        .startsWith("kotlinc-" + PacksOutils.KOTLIN.version),
                "version inattendue : " + pilote.toolVersion());
        assertTrue(pilote.toolVersion().endsWith("-forked"));
    }

    /** Lot X — une ouvrière morte au PREMIER lot (sortie 134, la
     *  signature du terrain : un abort d'ART) se SURVIT : la reprise
     *  fourque une ouvrière neuve et le lot réussit, l'échec
     *  intermédiaire ne remonte pas. */
    @Test
    void uneOuvriereMorteEstSurviveeParUneNeuve(@TempDir Path dossier)
            throws Exception {
        Path sortie = dossier.resolve("sortie-reprise");
        Files.createDirectories(sortie);
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun salut(): Int = 40 + 2\n");
        String ancienneVm = System.getProperty("joide.kotlinVm");
        System.setProperty("joide.kotlinVm", fausseVm(dossier,
                "echo \"Abort message: test-morte-ouvriere\" >&2\n"
                        + "sleep 0.5\n"
                        + "exit 134\n").toString());
        try {
            KotlinForkedDriver pilote = new KotlinForkedDriver();
            CompileResult resultat = pilote.compile(requete(source,
                    sortie));
            assertTrue(resultat.success,
                    () -> "la reprise sur ouvrière neuve doit réussir : "
                            + resultat.diagnostics);
            assertTrue(Files.isRegularFile(
                            sortie.resolve("demo/SalutKt.class")),
                    "la classe écrite manque : "
                            + resultat.writtenClasses);
            assertTrue(pilote.ouvriereVivante(),
                    "l'ouvrière neuve doit vivre pour les lots suivants");
        } finally {
            restaurerVm(ancienneVm);
        }
    }

    /** Lot XII — une ouvrière qui meurt TROIS fois (les reprises
     *  descendent l'échelle de démarrage) échoue le lot NOMMÉ : les
     *  trois codes de sortie, les tas essayés, la queue du stderr
     *  (« Abort message: … » d'ART atteint la console), le journal
     *  complet sur le disque. */
    @Test
    void troisMortsNommentLEchecAvecLeStderr(@TempDir Path dossier)
            throws Exception {
        Path sortie = dossier.resolve("sortie-mortes");
        Files.createDirectories(sortie);
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun salut(): Int = 40 + 2\n");
        String ancienneVm = System.getProperty("joide.kotlinVm");
        System.setProperty("joide.kotlinVm", fausseVmMorte(dossier,
                "echo \"Abort message: test-morte-ouvriere\" >&2\n"
                        + "sleep 0.5\n"
                        + "exit 134\n").toString());
        try {
            KotlinForkedDriver pilote = new KotlinForkedDriver();
            CompileResult resultat = pilote.compile(requete(source,
                    sortie));
            assertFalse(resultat.success, "le lot doit échouer");
            String message = resultat.diagnostics.get(0).message();
            assertTrue(message.contains("TROIS tentatives"),
                    "les trois tentatives doivent se voir : " + message);
            assertTrue(message.contains("sortie 134 puis 134 puis 134"),
                    "les trois codes de sortie doivent se nommer : "
                            + message);
            assertTrue(message.contains("tas essayés : "),
                    "les tas essayés doivent se nommer : " + message);
            assertTrue(message.contains("Abort message: "
                            + "test-morte-ouvriere"),
                    "la queue du stderr doit porter la cause : "
                            + message);
            assertTrue(Files.isRegularFile(
                    KotlinForkedDriver.cheminJournalStderr()),
                    "le journal stderr doit exister sur le disque");
            assertFalse(pilote.ouvriereVivante(),
                    "aucune ouvrière ne doit survivre à trois morts");
        } finally {
            restaurerVm(ancienneVm);
        }
    }

    /** Lot X — une ouvrière VIVANTE mais muette est bornée par le
     *  délai : le lot échoue nommé au lieu de pendre le build. */
    @Test
    void uneOuvriereMuetteEstBorneeParLeDelai(@TempDir Path dossier)
            throws Exception {
        Path sortie = dossier.resolve("sortie-muette");
        Files.createDirectories(sortie);
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun salut(): Int = 40 + 2\n");
        String ancienneVm = System.getProperty("joide.kotlinVm");
        String ancienDelai = System.getProperty(
                "joide.kotlinWorkerTimeoutSec");
        System.setProperty("joide.kotlinVm", fausseVmMorte(dossier,
                "sleep 30\n").toString());
        System.setProperty("joide.kotlinWorkerTimeoutSec", "2");
        try {
            KotlinForkedDriver pilote = new KotlinForkedDriver();
            CompileResult resultat = pilote.compile(requete(source,
                    sortie));
            assertFalse(resultat.success, "le lot doit échouer");
            String message = resultat.diagnostics.get(0).message();
            assertTrue(message.contains("n'a pas répondu en 2 s"),
                    "le silence doit être borné et nommé : " + message);
            assertFalse(pilote.ouvriereVivante(),
                    "l'ouvrière muette doit être tuée");
        } finally {
            if (ancienDelai != null) {
                System.setProperty("joide.kotlinWorkerTimeoutSec",
                        ancienDelai);
            } else {
                System.clearProperty("joide.kotlinWorkerTimeoutSec");
            }
            restaurerVm(ancienneVm);
        }
    }

    /** Lot X — l'échelle de tas : un tas dont la RÉSERVATION (deux
     *  fois le tas) dépasse la moitié de la RAM est rejeté (une VM
     *  qui ne peut pas réserver son espace de régions ABORTE), et
     *  une RAM inconnue garde l'échelle entière. */
    @Test
    void lesTasAuDelaDeLaMoitieDeLaRamSontRejetes() {
        List<Integer> echelle = KotlinForkedDriver.tasAbordables(1024,
                3000L);
        assertTrue(echelle.equals(List.of(512, 384)),
                "3 Go de RAM ne soutiennent que 512/384 Mo : "
                        + echelle);
        List<Integer> entiere = KotlinForkedDriver.tasAbordables(1024,
                0L);
        assertTrue(entiere.equals(List.of(1024, 768, 512, 384)),
                "une RAM inconnue garde l'échelle entière : " + entiere);
    }

    /** Lot XII — l'échelle de REMONTÉE (une morte APRÈS réponse : le
     *  tas essayé était trop petit, l'OOM d'ART aborte sans message)
     *  : la garde ASSOULIE (rung × 2 ≤ 80 % de la RAM) laisse
     *  gravir 1024 sur 3 Go et 1536 sur 4 Go — des rungs que la
     *  garde de DÉMARRAGE refusait de justesse — et une RAM inconnue
     *  garde tout, trié ascendante. */
    @Test
    void lEchelleDeRemonteeMontePlusHautQueLEchelleDeDemarrage() {
        List<Integer> echelle3Go = KotlinForkedDriver.tasAbordables(
                1024, 3000L);
        assertTrue(echelle3Go.equals(List.of(512, 384)),
                "la démarrage plafonne à 512/384 sur 3 Go : "
                        + echelle3Go);
        List<Integer> remontee3Go = KotlinForkedDriver.echelleRemontee(
                1024, echelle3Go, 3000L);
        assertTrue(remontee3Go.equals(List.of(384, 512, 1024)),
                "3 Go de RAM laissent gravir jusqu'à 1024 Mo : "
                        + remontee3Go);
        List<Integer> remontee4Go = KotlinForkedDriver.echelleRemontee(
                1024, List.of(512, 384), 4000L);
        assertTrue(remontee4Go.equals(List.of(384, 512, 1024, 1536)),
                "4 Go de RAM laissent gravir jusqu'à 1536 Mo : "
                        + remontee4Go);
        List<Integer> remonteeLibre = KotlinForkedDriver.echelleRemontee(
                1024, List.of(1024, 768, 512, 384), 0L);
        assertTrue(remonteeLibre.equals(
                        List.of(384, 512, 768, 1024, 1536)),
                "une RAM inconnue garde toute la remontée : "
                        + remonteeLibre);
    }

    /** Lot XII — une morte APRÈS réponse (la VM a rendu son statut
     *  puis a aborté, la signature d'un OOM mid-run d'ART : sortie
     *  134, stderr vide) fait MONTER la reprise d'un rung dans
     *  l'échelle de remontée, et le lot réussit au tas supérieur. */
    @Test
    void uneMorteApresReponseMonteDeRung(@TempDir Path dossier)
            throws Exception {
        Path sortie = dossier.resolve("sortie-remontee");
        Files.createDirectories(sortie);
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun salut(): Int = 40 + 2\n");
        Path rungs = dossier.resolve("rungs-journal");
        String ancienneVm = System.getProperty("joide.kotlinVm");
        // au PREMIER lancement : le statut OK puis l'abort (134,
        // sans stderr — le tas était trop petit) ; aux suivants, la
        // VRAIE ouvrière sert le lot au rung remonté
        System.setProperty("joide.kotlinVm", fausseVmAvecJournal(dossier,
                rungs, "echo \"OK\"\nsleep 0.3\nexit 134\n").toString());
        try {
            KotlinForkedDriver pilote = new KotlinForkedDriver();
            CompileResult resultat = pilote.compile(requete(source,
                    sortie));
            assertTrue(resultat.success,
                    () -> "la reprise remontée doit réussir : "
                            + resultat.diagnostics);
            assertTrue(Files.isRegularFile(
                            sortie.resolve("demo/SalutKt.class")),
                    "la classe écrite manque : "
                            + resultat.writtenClasses);
            List<String> tas = Files.readAllLines(rungs).stream()
                    .map(ligne -> ligne.replace("-Xmx", "")
                            .replace("m", ""))
                    .toList();
            List<Integer> echelle = KotlinForkedDriver.tasAbordables(
                    1024, KotlinForkedDriver.memoireTotaleMo());
            List<Integer> remontee = KotlinForkedDriver.echelleRemontee(
                    1024, echelle,
                    KotlinForkedDriver.memoireTotaleMo());
            int premier = echelle.get(0);
            int monte = remontee.stream().filter(r -> r > premier)
                    .findFirst().orElse(premier);
            assertTrue(tas.size() >= 2,
                    "les deux lancements doivent se journaliser : "
                            + tas);
            assertEquals(String.valueOf(premier), tas.get(0),
                    "le premier lancement part du haut de l'échelle : "
                            + tas);
            assertEquals(String.valueOf(monte), tas.get(1),
                    "la reprise APRÈS réponse doit MONTER de rung : "
                            + tas + " (remontée " + remontee + ")");
            assertTrue(monte > premier,
                    "le rung remonté doit être plus grand que le "
                            + "premier : " + tas);
        } finally {
            restaurerVm(ancienneVm);
        }
    }

    /** Le tas se lit en unités (g, m, nu) et se borne aux plafonds. */
    @Test
    void leTasSeParseEnUnites() {
        assertTrue(KotlinForkedDriver.parseTas("1g") == 1024);
        assertTrue(KotlinForkedDriver.parseTas("768m") == 768);
        assertTrue(KotlinForkedDriver.parseTas("512") == 512);
        assertTrue(KotlinForkedDriver.parseTas("100m") == 256,
                "sous le plafond bas : borné");
        assertTrue(KotlinForkedDriver.parseTas("8g") == 4096,
                "au-delà du plafond haut : borné");
    }

    /** Le drapeau d'inlining JIT ne part QUE sur ART : sur le bureau
     *  (java), un drapeau inconnu tuerait la VM au démarrage. */
    @Test
    void leDrapeauJitNePartPasSurLeBureau() {
        assertFalse(KotlinForkedDriver.inliningJitDesactive(),
                "sur le bureau (SDK inconnu, binaire java), le drapeau "
                        + "ART ne doit pas partir");
    }

    /** Une fausse VM : un script exécutable qui joue le corps donné
     *  au lieu de démarrer le compilateur (les morts du terrain sans
     *  ART dans les essais du bureau). Au PREMIER lancement elle
     *  joue le corps ; aux suivants elle sert le VRAI ouvrier par
     *  {@code java} — la reprise du pilote touche donc une ouvrière
     *  saine. */
    private static Path fausseVm(Path dossier, String corps)
            throws Exception {
        Path marqueur = dossier.resolve("fausse-vm-marqueur");
        Path script = dossier.resolve("fausse-vm.sh");
        String java = Path.of(System.getProperty("java.home"), "bin",
                "java").toString();
        Files.writeString(script, "#!/bin/sh\n"
                + "if [ -e \"" + marqueur + "\" ]; then\n"
                + "  exec \"" + java + "\" \"$@\"\n"
                + "fi\n"
                + "touch \"" + marqueur + "\"\n"
                + corps);
        script.toFile().setExecutable(true);
        return script;
    }

    /** La même, mais qui meurt À CHAQUE lancement (une appareil où
     *  la reprise ne sauve rien — le lot doit échouer nommé). */
    private static Path fausseVmMorte(Path dossier, String corps)
            throws Exception {
        Path script = dossier.resolve("fausse-vm-morte.sh");
        Files.writeString(script, "#!/bin/sh\n" + corps);
        script.toFile().setExecutable(true);
        return script;
    }

    /** La fausse VM qui JOURNALISE son {@code -Xmx} à CHAQUE
     *  lancement (le premier corps, puis la vraie ouvrière par
     *  {@code java}) — pour voir les rungs réellement essayés. */
    private static Path fausseVmAvecJournal(Path dossier, Path journal,
                                            String corps)
            throws Exception {
        Path marqueur = dossier.resolve("fausse-vm-marqueur-journal");
        Path script = dossier.resolve("fausse-vm-journal.sh");
        String java = Path.of(System.getProperty("java.home"), "bin",
                "java").toString();
        Files.writeString(script, "#!/bin/sh\n"
                + "echo \"$1\" >> \"" + journal + "\"\n"
                + "if [ -e \"" + marqueur + "\" ]; then\n"
                + "  exec \"" + java + "\" \"$@\"\n"
                + "fi\n"
                + "touch \"" + marqueur + "\"\n"
                + corps);
        script.toFile().setExecutable(true);
        return script;
    }

    private static void restaurerVm(String ancienneVm) {
        if (ancienneVm != null) {
            System.setProperty("joide.kotlinVm", ancienneVm);
        } else {
            System.clearProperty("joide.kotlinVm");
        }
    }

    private static CompileRequest requete(Path source, Path sortie)
            throws Exception {
        return new CompileRequest(List.of(source),
                List.of(jarDe(kotlin.KotlinVersion.class)), List.of(),
                sortie, "17", "UTF-8", false);
    }

    /**
     * Le classpath de l'ouvrière : le classpath d'essai ENTIERS porte
     * déjà l'ouvrier (workerKotlin) et la fermeture runtime du
     * compilateur (les dépendances de son POM) — sur l'appareil, le
     * pack différé tient ce rôle (« l'APK de l'app lui sert de
     * classpath », la manière CodeAssist).
     */
    private static String classpathOuvrier() throws Exception {
        return System.getProperty("java.class.path")
                + File.pathSeparator
                + jarDe(KotlinWorkerMain.class);
    }

    private static Path jarDe(Class<?> classe) throws Exception {
        return Path.of(classe.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
    }
}
