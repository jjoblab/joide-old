package jo.joide.app.android.crash;

import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jo.joide.platform.AtomicFiles;

/**
 * Lot C1 — LE GARDE-CRASH : l'intercepteur des plantages de TOUT type,
 * posé au démarrage du processus par {@code AppJoide}. Il ne répare
 * rien (un crash reste un crash) : il le NOMME, l'écrit sur le disque
 * et le montre à l'écran de crash — « détecter tout le type crash »
 * demandé par le terrain du 2026-11-19 (l'application plantait au
 * tiroir agent et à la saisie des clés SANS trace récupérable).
 *
 * <p>Trois voies de détection :</p>
 * <ul>
 * <li><b>les exceptions non interceptées, SUR TOUT FIL</b> —
 * {@code Thread.setDefaultUncaughtExceptionHandler} attrape chaque
 * fracture du processus (le fil principal comme les fils de fond :
 * import, build, agent) ; la classe exacte (NullPointerException,
 * InflateException, NoSuchMethodError, OutOfMemoryError…) part au
 * rapport avec la pile complète ;</li>
 * <li><b>le gel du fil d'interface</b> (classe ANR) — un chien de
 * garde poste une impulsion au fil principal toutes les 2 s ; si
 * l'impulsion ne court pas en {@value #SEUIL_GEL_S} s, le fil est
 * gelé : la PILE du fil principal au moment du gel est capturée
 * (C'EST le diagnostic — où il est bloqué), pas seulement le
 * symptôme ;</li>
 * <li><b>les fractures des processus secondaires</b> ({@code :build},
 * démon de build) : rapportées sur le disque aussi — la feuille de
 * build « n'affichait rien » précisément quand le démon mourait :
 * le rapport le dira désormais nommément (l'écran de crash ne se
 * lance pas : le processus principal vit, l'utilisateur travaille).</li>
 * </ul>
 *
 * <p>Le rapport vit dans {@code filesDir/crash/} (au plus
 * {@value #MAX_RAPPORTS}, les plus anciens s'effacent), écriture
 * atomique, format {@code joide.crash/1} porté par
 * {@link RapportCrash} (pur, testé JVM). L'écran
 * {@link EcranCrash} se lance en tâche neuve PUIS le processus meurt
 * proprement : le système relance l'écran dans un processus neuf
 * (la manière CustomActivityOnCrash — jamais un zombie).</p>
 *
 * <p>Résilience propre : tout ce que le garde fait est sous garde —
 * un garde qui planterait à son tour délègue au gestionnaire système
 * d'origine, la boucle est impossible (garde de réentrance).</p>
 */
public final class GardienCrash {

    /** Le dossier des rapports (filesDir/crash). */
    public static final String DOSSIER = "crash";

    /** Le plafond de rapports conservés (les plus anciens s'effacent). */
    public static final int MAX_RAPPORTS = 20;

    /** Le seuil de gel du fil d'interface (secondes, cf. ANR système). */
    public static final int SEUIL_GEL_S = 8;

    /** La cadence des impulsions du chien de garde (ms). */
    private static final long CADENCE_MS = 2_000L;

    /** La pause laissée au lancement de l'écran avant la mort du
     *  processus (ms) — AMS doit encaisser la tâche neuve. */
    private static final long MORT_APRES_MS = 600;

    private static final AtomicBoolean pose = new AtomicBoolean(false);
    private static final AtomicBoolean enTraitement =
            new AtomicBoolean(false);

    private static volatile Context contexte;
    private static volatile Thread.UncaughtExceptionHandler precedent;

    private GardienCrash() {
    }

    /**
     * Pose le garde sur CE processus (appelé par {@code AppJoide} à
     * la création, AVANT toute activité — un plantage du premier
     * gonflage est déjà attrapé).
     *
     * @param application le contexte d'application (vit plus que
     *                    toute activité)
     */
    public static void installer(Context application) {
        if (!pose.compareAndSet(false, true)) {
            return;
        }
        contexte = application.getApplicationContext();
        precedent = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(GardienCrash::surPanne);
        if (processusPrincipal()) {
            demarrerChienDeGarde();
        }
    }

    /**
     * CE processus est-il le processus principal (pas {@code :build}) ?
     * — le chien de garde et l'écran de crash ne vivent qu'ici.
     * {@code Application.getProcessName()} est posé exactement à
     * l'API 28 (le minSdk du produit) : aucune garde nécessaire.
     */
    private static boolean processusPrincipal() {
        if (contexte == null) {
            return false;
        }
        try {
            return contexte.getPackageName().equals(
                    android.app.Application.getProcessName());
        } catch (RuntimeException e) {
            // un nom illisible : l'honnêteté vaut le principal (le
            // garde ne doit jamais être muet)
            return true;
        }
    }

    // ------------------------------------------------------------------
    // La voie 1 : l'exception non interceptée, sur tout fil
    // ------------------------------------------------------------------

    private static void surPanne(Thread fil, Throwable panne) {
        if (!enTraitement.compareAndSet(false, true)) {
            // déjà en traitement : le gestionnaire d'origine finit
            // le travail, jamais une boucle de garde
            if (precedent != null) {
                precedent.uncaughtException(fil, panne);
            }
            return;
        }
        try {
            ecrire(RapportCrash.de(System.currentTimeMillis(),
                    fil == null ? "?" : fil.getName(), panne,
                    pileDuFilPrincipal()));
            if (processusPrincipal()) {
                // le processus principal : l'écran de crash se montre
                // PUIS le processus meurt proprement (le système
                // relance l'écran dans un processus neuf)
                lancerEcran();
                mourir();
                return;
            }
            // un processus secondaire (:build) : le rapport est
            // écrit, l'écran ne s'impose pas sur l'utilisateur qui
            // travaille dans l'atelier — le système traite la mort
        } catch (Throwable tout) {
            // le garde lui-même en échec : le rapport a peut-être été
            // écrit, l'écran est un luxe — le système reprend la main
        } finally {
            enTraitement.set(false);
        }
        if (precedent != null) {
            precedent.uncaughtException(fil, panne);
        }
    }

    /**
     * La pile du fil principal AU MOMENT de la panne (le contexte où
     * l'UI vivait quand un fil de fond a cassé — la moitié du
     * diagnostic, gratuitement).
     */
    private static String pileDuFilPrincipal() {
        try {
            Thread fil = Looper.getMainLooper().getThread();
            StringBuilder pile = new StringBuilder();
            for (StackTraceElement element : fil.getStackTrace()) {
                pile.append("  at ").append(element.toString())
                        .append('\n');
            }
            return pile.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // La voie 2 : le gel du fil d'interface (ANR)
    // ------------------------------------------------------------------

    /**
     * Le chien de garde : une impulsion au fil principal toutes les
     * {@value #CADENCE_MS} ms ; l'impulsion non courue en
     * {@value #SEUIL_GEL_S} s = gel (le fil ne tourne plus — où
     * est-il bloqué ?). La PILE du fil principal au moment du gel
     * part au rapport : c'est le diagnostic, pas seulement le
     * symptôme. Un gel avéré se traite comme une fracture : rapport,
     * écran, mort propre (l'état gelé ne sert à personne, l'ANR
     * système ne ferait que dire « ne répond pas »).
     */
    private static void demarrerChienDeGarde() {
        Handler fil = new Handler(Looper.getMainLooper());
        // zéro : aucune impulsion en attente ; non nul : le moment de
        // la pose — l'impulsion revenue, le chien se réarme.
        // ATOMIQUE : l'impulsion s'écrit depuis le fil principal, le
        // chien lit depuis son fil — un long nu se déchire sur ARM
        // 32 bits, jamais de faux gel de lecture
        java.util.concurrent.atomic.AtomicLong attente =
                new java.util.concurrent.atomic.AtomicLong(0L);
        Runnable impulsion = () -> attente.set(0L);
        Thread garde = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(CADENCE_MS);
                } catch (InterruptedException fin) {
                    return;
                }
                if (Debug.isDebuggerConnected()) {
                    // un point d'arrêt suspend le fil principal
                    // légitimement : jamais de faux gel sous débogueur
                    attente.set(0L);
                    continue;
                }
                long pose = attente.get();
                if (pose == 0L) {
                    attente.compareAndSet(0L, System.currentTimeMillis());
                    fil.post(impulsion);
                    continue;
                }
                if (System.currentTimeMillis() - pose
                        < SEUIL_GEL_S * 1000L) {
                    continue; // l'impulsion peut encore courir
                }
                // GEL avéré : la pile du fil principal se capture
                // DEPUIS le chien de garde (le fil principal est
                // bloqué, il ne rendra jamais la pile lui-même)
                try {
                    ecrire(RapportCrash.gel(
                            System.currentTimeMillis(),
                            pileDuFilPrincipal(), SEUIL_GEL_S));
                    lancerEcran();
                    mourir();
                } catch (Throwable tout) {
                    // le rapport est un luxe ici : le gel est déjà
                    // avéré, le système ANR suivra son cours
                } finally {
                    // le chien ne tire qu'UNE fois par gel : l'impulsion
                    // revenue, il se réarme — mais le processus meurt
                    // déjà (mourir l'a programmée)
                    attente.set(0L);
                }
                return; // un gel avéré : ce chien meurt avec le
                // processus (mourir() l'a programmée)
            }
        }, "joide-garde-crash");
        garde.setDaemon(true);
        garde.start();
    }

    // ------------------------------------------------------------------
    // Le rapport sur le disque : écriture atomique, plafonnée
    // ------------------------------------------------------------------

    /**
     * Écrit le rapport (atomique) et ROGNE le dossier au plafond.
     *
     * @param rapport le rapport à écrire
     * @throws IOException l'écriture a échoué
     */
    private static void ecrire(RapportCrash rapport) throws IOException {
        Path dossier = dossier();
        Files.createDirectories(dossier);
        AtomicFiles.writeBytes(dossier.resolve(rapport.nomFichier()),
                rapport.texte(version(), androidVersion(), appareil())
                        .getBytes(StandardCharsets.UTF_8));
        rogner(dossier);
    }

    /**
     * Le dossier des rapports ({@code filesDir/crash}).
     *
     * @return le chemin, lisible aussi par l'écran de crash
     */
    public static Path dossier() {
        return Paths.get(contexte.getFilesDir().getAbsolutePath(),
                DOSSIER);
    }

    /** Les rapports présents, du plus récent au plus ancien. */
    public static List<Path> rapports() {
        List<Path> fichiers = new ArrayList<>();
        try (java.util.stream.Stream<Path> chemins =
                     Files.list(dossier())) {
            chemins.filter(p -> p.getFileName().toString()
                            .startsWith("crash-")
                            && p.getFileName().toString().endsWith(".txt"))
                    .forEach(fichiers::add);
        } catch (IOException | RuntimeException e) {
            // dossier absent : aucun rapport (premier lancement)
        }
        fichiers.sort(Comparator.comparing(
                p -> p.getFileName().toString(), Comparator.reverseOrder()));
        return fichiers;
    }

    /** Retire les plus anciens au-delà du plafond. */
    private static void rogner(Path dossier) {
        List<Path> anciens = new ArrayList<>();
        try (java.util.stream.Stream<Path> chemins =
                     Files.list(dossier)) {
            chemins.filter(p -> p.getFileName().toString()
                            .startsWith("crash-"))
                    .sorted(Comparator.comparing(
                            p -> p.getFileName().toString(),
                            Comparator.reverseOrder()))
                    .forEach(anciens::add);
        } catch (IOException | RuntimeException e) {
            return;
        }
        for (int i = MAX_RAPPORTS; i < anciens.size(); i++) {
            try {
                Files.deleteIfExists(anciens.get(i));
            } catch (IOException | RuntimeException e) {
                // la suppression au mieux : le rapport suivant rogne
                // encore
            }
        }
    }

    /**
     * Le contenu d'un rapport, ou la chaîne vide (jamais une
     * exception à l'écran de crash).
     *
     * @param rapport le fichier du rapport
     * @return le texte intégral (vide si illisible)
     */
    public static String lire(Path rapport) {
        try {
            return new String(Files.readAllBytes(rapport),
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Efface tous les rapports (l'action de l'écran de crash). */
    public static void effacer() {
        for (Path rapport : rapports()) {
            try {
                Files.deleteIfExists(rapport);
            } catch (IOException | RuntimeException e) {
                // l'effacement au mieux
            }
        }
    }

    // ------------------------------------------------------------------
    // Le filet de sécurité : les rapports jamais VUS se montrent au
    // retour dans l'application (la remontée du terrain du 2026-11-19 :
    // un plantage du démon :build laissait la feuille MUETTE — aucun
    // écran ne se lance pour ne pas voler l'atelier vivant, le rapport
    // attendait en silence ; désormais l'accueil le montre au retour)
    // ------------------------------------------------------------------

    /** Le marqueur de dernière consultation des rapports. */
    private static Path marqueur() {
        return dossier().resolve(".vus");
    }

    /**
     * Des rapports ont-ils été écrits depuis la dernière consultation
     * de l'écran de crash ? — l'accueil l'ouvre alors AU RETOUR (un
     * plantage ne doit jamais mourir en silence, même quand son écran
     * n'a pas pu se lancer à l'instant du plantage).
     *
     * @return vrai si au moins un rapport est plus récent que la
     *         dernière consultation
     */
    public static boolean rapportsNouveaux() {
        long vu = momentVu();
        boolean nouveaux = false;
        for (Path rapport : rapports()) {
            try {
                if (Files.getLastModifiedTime(rapport).toMillis() > vu) {
                    nouveaux = true;
                    break;
                }
            } catch (IOException | RuntimeException e) {
                // un fichier illisible ne masque pas les autres
            }
        }
        return nouveaux;
    }

    /** Le moment de la dernière consultation des rapports (0 jamais). */
    private static long momentVu() {
        try {
            Path marque = marqueur();
            if (!Files.isRegularFile(marque)) {
                return 0L;
            }
            return Long.parseLong(new String(
                    Files.readAllBytes(marque),
                    StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException e) {
            return 0L;
        }
    }

    /** Marque les rapports comme consultés (l'écran de crash l'ouvre). */
    public static void marquerVus() {
        try {
            Files.createDirectories(dossier());
            AtomicFiles.writeBytes(marqueur(),
                    String.valueOf(System.currentTimeMillis())
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // le marqueur au mieux : un rapport déjà vu peut se
            // remontrer, jamais l'inverse
        }
    }

    // ------------------------------------------------------------------
    // L'écran de crash et la mort propre du processus
    // ------------------------------------------------------------------

    /**
     * Lance l'écran de crash en tâche NEUVE (la pile morte se vide,
     * l'écran vivra dans un processus relancé par le système).
     */
    private static void lancerEcran() {
        try {
            Intent intention = new Intent(contexte, EcranCrash.class);
            intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            contexte.startActivity(intention);
        } catch (RuntimeException e) {
            // le lancement peut être refusé (app en arrière-plan) :
            // le rapport vit déjà sur le disque, l'écran le montrera
            // à la prochaine ouverture
        }
    }

    /**
     * La mort propre : le fil plante déjà (son looper s'arrête),
     * laisser le processus vivre est l'état zombie — un fil différé
     * tue le processus APRÈS que AMS a encaissé la tâche de l'écran.
     */
    private static void mourir() {
        Thread fin = new Thread(() -> {
            try {
                Thread.sleep(MORT_APRES_MS);
            } catch (InterruptedException e) {
                // le tueur s'exécute quand même
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }, "joide-crash-fin");
        fin.setDaemon(true);
        fin.start();
    }

    // ------------------------------------------------------------------
    // Les métadonnées du rapport (toujours remplies, jamais jetables)
    // ------------------------------------------------------------------

    /** La version de l'application (nom + SHA du build). */
    public static String version() {
        try {
            return String.valueOf(jo.joide.app.android.BuildConfig
                    .VERSION_NAME) + " (" + jo.joide.app.android.BuildConfig
                    .JOIDE_SHA + ")";
        } catch (Throwable e) {
            return "?";
        }
    }

    /** La version d'Android de l'appareil (lisible, pas un code). */
    private static String androidVersion() {
        try {
            return android.os.Build.VERSION.RELEASE + " (API "
                    + android.os.Build.VERSION.SDK_INT + ")";
        } catch (Throwable e) {
            return "?";
        }
    }

    /** Le nom de l'appareil (fabriquant + modèle). */
    private static String appareil() {
        try {
            return String.valueOf(android.os.Build.MANUFACTURER) + " "
                    + android.os.Build.MODEL;
        } catch (Throwable e) {
            return "?";
        }
    }
}
