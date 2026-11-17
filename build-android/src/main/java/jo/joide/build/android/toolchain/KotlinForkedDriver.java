package jo.joide.build.android.toolchain;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jo.joide.build.jvm.compile.CompileInventory;
import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.build.jvm.compile.KotlinArgs;
import jo.joide.platform.Problem;

/**
 * Pilote du compilateur Kotlin par VM FORKÉE (lot VII, la manière
 * CodeAssist {@code ForkedKotlinCompiler}) : l'APK allégé ne porte
 * plus les 43 Mo de dex du compilateur — une VM non-zygote
 * ({@code /apex/com.android.art/bin/dalvikvm64}) le charge depuis le
 * pack de dex différé, avec un tas qui dépasse le plafond
 * {@code largeHeap} de l'application : les grosses compilations ne
 * se heurtent plus au mur mémoire du processus app.
 *
 * <p>La VM ouvrière est RÉUTILISÉE entre les lots (un démarrage de
 * compilateur ne se paie qu'une fois) ; l'échange est SÉRIALISÉ —
 * le protocole ligne est mono-serveur. Le pilote ne référence
 * AUCUNE classe du compilateur : seuls des tampons et des
 * {@code String} traversent le tuyau (le mur étanche de l'ADR-067
 * tient). Les arguments sont bâtis par {@link KotlinArgs} — les
 * MÊMES que le pilote embarqué — et les classes écrites se détectent
 * par différence d'inventaire du répertoire de sortie, côté
 * appelant.</p>
 *
 * <p><b>Lot X — une morte d'ouvrière NOMMÉE et SURVÉCUE</b> (le
 * terrain du 2026-11-17 : « l'ouvrière Kotlin est morte (sortie 134) »
 * sur le premier lot {@code :core:compileKotlin}, la cause réelle
 * restée dans logcar parce que le stderr était hérité). La manière
 * CodeAssist (son #1748, la même signature : l'ouvrière ART meurt
 * d'un {@code SIGABRT}) :</p>
 *
 * <ul>
 * <li><b>Le stderr est CAPTURÉ</b> — jamais hérité : une queue de
 * quarante lignes sert au message d'échec (« Abort message: … » d'ART
 * atteint la console du build), et le journal complet va dans
 * {@code ouvriere-kotlin.err} du répertoire d'outillage. Une queue
 * non lue peut aussi BLOQUER l'ouvrière quand le tampon du tuyau
 * se remplit — l'héritage ne lissait ce risque que par chance ;</li>
 * <li><b>Le JIT d'ART est dompté</b> : sur Android 11 et plus ancien
 * (SDK ≤ 30), le JIT malcompile le code FIR du compilateur quand il
 * inline à travers un cache d'appel polymorphe — crashes dans
 * {@code HInliner::TryInlinePolymorphicCall} (CodeAssist #1748). La
 * VM ouvrière démarre donc avec {@code -Xcompiler-option
 * --inline-max-code-units=0} : un budget d'inlining nul éteint
 * l'inliner du JIT, les méthodes compilent quand même ;</li>
 * <li><b>Le tas respecte la RAM</b> : ART réserve DEUX fois
 * {@code -Xmx} d'espace d'adresses (l'espace de régions du GC
 * copiant) — au-delà de la moitié de la RAM de l'appareil, la
 * réservation échoue et la VM ABORTE au démarrage, encore un 134.
 * L'échelle demandée (propriété {@code joide.kotlinWorkerHeap},
 * défaut 1 Gio) puis 768/512/384 Mo ne garde que les rungs que la
 * moitié de la RAM peut soutenir, et une morte SANS réponse (un
 * abort de démarrage) re-fourque au rung INFÉRIEUR, comme la
 * {@code startWorker} de CodeAssist descend sa {@code LADDER} ;</li>
 * <li><b>Une morte APRÈS réponse MONTE d'un rung</b> (lot XII) :
 * la VM démarrait bien — le tas essayé était trop PETIT pour le lot
 * (l'OOM d'ART au milieu d'un compile aborte SANS message : sortie
 * 134, stderr vide — le terrain Android 35, tas 512 Mo, deux morts).
 * Sur un appareil à ~4 Go, la garde de démarrage rejette 1024 de
 * justesse : l'échelle plafonne à 512 et un gros module n'y tient
 * pas. La remontée (lot 1536 inclus) passe à la garde ASSOULIE
 * (rung × 2 ≤ 80 % de la RAM — la réservation est virtuelle,
 * l'engagement réel suit l'usage, et l'autre option est un build qui
 * échoue) ;</li>
 * <li><b>Une morte au milieu d'un lot se SURVIT</b> : trois
 * tentatives par lot (la première réutilise l'ouvrière vivante, la
 * reprise fourque une NEUVE en montant ou descendant selon la cause).
 * Un échec qui survit au retry échoue le lot NOMMÉ (code de sortie
 * des tentatives, tas essayés, queue de stderr) — jamais un silence.
 * Sur un appareil Android ≤ 11, l'échec porte l'explication du #1748 ;
 * trois échecs complets abandonnent le fork pour la session (chaque
 * lot suivant échoue vite, sans re-payer les forcs) ;</li>
 * <li><b>Une ouvrière muette est bornée</b> : pas de réponse dans
 * {@code joide.kotlinWorkerTimeoutSec} (défaut 3600 s) et le lot
 * échoue nommé — une ouvrière bloquée ne pend plus le build.</li>
 * </ul>
 *
 * <p>Les drapeaux ne partent QUE à la VM ART : sur le bureau
 * ({@code joide.kotlinVm} = {@code java}) un drapeau inconnu tuerait
 * la VM au démarrage. Le débogage de la VM ouvrière reste possible
 * par {@code joide.kotlinWorkerCp} (classpath) et
 * {@code joide.toolchain.dir} (où vivent le journal stderr et le
 * tmpdir {@code kotlinc-tmp} — la VM en ligne de commande a un
 * tmpdir par défaut qui N'EXISTE PAS sous le bac à sable d'une app,
 * et le compilateur y crée des fichiers temporaires).</p>
 */
public final class KotlinForkedDriver implements CompilerDriver {

    /** L'ouvrier du pack (jo.joide.build.jvm.worker, source set
     *  workerKotlin de build-jvm). */
    static final String OUVRIER
            = "jo.joide.build.jvm.worker.KotlinWorkerMain";

    /** Les binaires candidats de la VM non-zygote, par ordre de
     *  préférence (ART APEX d'abord — Android 10+). */
    private static final String[] BINAIRES_VM = {
            "/apex/com.android.art/bin/dalvikvm64",
            "/system/bin/dalvikvm64",
            "/system/bin/dalvikvm"
    };

    /** Le tas par défaut de l'ouvrière (ADR-069 : 1 Gio). */
    private static final int TAS_DEFAUT_MO = 1024;
    private static final int TAS_MIN_MO = 256;
    private static final int TAS_MAX_MO = 4096;

    /** Les rungs de secours de l'échelle de tas, quand le rung
     *  demandé meurt sans avoir répondu (un abort de démarrage) —
     *  la LADDER de CodeAssist. Ils ne servent JAMAIS au premier
     *  fork : seulement à la descente. */
    static final int[] ECHELLE_SECOURS_MO = {768, 512, 384};

    /** ART réserve DEUX fois {@code -Xmx} d'espace d'adresses pour
     *  l'espace de régions du GC copiant concurrent (ForkedToolVm de
     *  CodeAssist) ; au-delà de la moitié de la RAM, la réservation
     *  échoue et la VM aborte. */
    static final long FACTEUR_RESERVATION = 2L;
    static final double FRACTION_BUDGET_RAM = 0.5;

    /** Le rung de secours AU-DESSUS du tas demandé, pour la REMONTÉE
     *  (lot XII : un lot qui déborde du rung éprouvé) — jamais au
     *  premier fork. */
    static final int RUNG_REMONTEE_SUPPLEMENTAIRE_MO = 1536;

    /** La fraction de RAM pour la garde de la REMONTÉE — plus lâche
     *  que la garde de démarrage : la VM démarre déjà au rung essayé
     *  (la réservation est virtuelle, l'engagement réel suit
     *  l'usage), et l'autre option est un build qui échoue. */
    static final double FRACTION_REMONTEE_RAM = 0.8;

    /** Le délai par défaut d'une réponse d'ouvrière (une compile
     *  lente d'un gros module sur appareil modeste reste dedans). */
    private static final long DELAI_REPONSE_DEFAUT_SEC = 3600L;

    /** Les lignes de stderr gardées pour nommer une morte. */
    private static final int LIGNES_QUEUE_STDERR = 40;

    /** Les échecs complets (les DEUX tentatives ratées) avant
     *  d'abandonner le fork pour la session. */
    private static final int ECHECS_AVANT_ABANDON = 3;

    /** Le SDK Android sous lequel le JIT ART est connu pour
     *  malcompiler le compilateur (CodeAssist #1748 : aucun rapport
     *  sur API 31 ou plus récent). */
    private static final int SDK_JIT_CRASHES = 30;

    /** Le nombre de tentatives par lot : la première réutilise
     *  l'ouvrière vivante, la reprise fourque une NEUVE — en
     *  DESCENDANT après une morte sans réponse (abort de démarrage),
     *  en MONTANT après une morte après réponse (le tas était trop
     *  petit, lot XII). */
    private static final int TENTATIVES = 3;

    private final int tasDemandeMo;
    private final Object verrou = new Object();
    private Process ouvriere;
    private BufferedWriter entree;
    private FileDeReponse reponses;
    private JournalOuvriere journal;
    private int rungEffectifMo;
    /** Le rung de la dernière ouvrière ayant RÉPONDU : un re-fork
     *  part de là plutôt que de remonter à un tas déjà abortif. */
    private int rungEprouveMo;
    /** L'ouvrière vivante a déjà RÉPONDU à un lot : une morte sans
     *  réponse pointe un abort de démarrage (descente de l'échelle),
     *  une morte après réponse pointe le lot lui-même. */
    private boolean ouvriereEprouvee;
    private int echecsSession;
    private String raisonAbandon;
    /** La queue de stderr de la dernière ouvrière tuée (le journal
     *  part avec elle — l'échec nommé la porte encore). */
    private String queueDerniere = "";

    /** Construit le pilote avec le tas ouvrier par défaut (1 Gio,
     *  surchargeable par {@code joide.kotlinWorkerHeap}). */
    public KotlinForkedDriver() {
        this(tasDemande());
    }

    /**
     * @param tasMax plafond de tas de la VM ouvrière (« 1g », « 768m »)
     */
    public KotlinForkedDriver(String tasMax) {
        this(parseTas(tasMax));
    }

    private KotlinForkedDriver(int tasDemandeMo) {
        this.tasDemandeMo = tasDemandeMo;
    }

    @Override
    public String toolVersion() {
        return "kotlinc-" + PacksOutils.KOTLIN.version + "-forked";
    }

    @Override
    public CompileResult compile(CompileRequest request) {
        Map<String, CompileInventory.SizeAndTime> avant =
                CompileInventory.of(request.outputDir);
        // le pack peut devoir se télécharger (UNE fois) : le message
        // d'échec porte l'URL épinglée ; le jar ORIGINAL de la stdlib
        // (lot XVI) s'extrait des ressources du pack AVANT la
        // construction des arguments — le classpath de compilation
        // exige des jar RÉELS (le JAR FS du compilateur ne lit pas
        // les dex du pack) et {@code -no-stdlib} en fait une entrée
        // obligatoire (la manière CodeAssist : le jar réel téléchargé
        // ; ici le pack le porte, épinglé par son SHA)
        String classpath;
        Path stdlib;
        Path home;
        try {
            classpath = classpathOuvriere();
            home = homeOuvriere(classpath);
            stdlib = jarStdlib(home);
        } catch (IOException e) {
            return new CompileResult(false, List.of(Problem.error(
                    "kotlin.worker",
                    "compilateur Kotlin différé indisponible : "
                            + e.getMessage())), List.of());
        }
        List<Path> plugins;
        try {
            // lot XIX : les plugins par sonde de classpath (Compose) —
            // le jar du plugin sort du pack comme la stdlib (jars/) ;
            // le registrar est DEXÉ dans le pack, la VM ouvrière le
            // charge (sur ART, un jar ne définit pas de classes)
            plugins = jo.joide.build.jvm.compile.PluginsKotlin
                    .pluginsPour(request, home);
        } catch (IOException e) {
            return new CompileResult(false, List.of(Problem.error(
                    "kotlin.plugin-error", e.getMessage())), List.of());
        }
        List<String> args;
        try {
            args = KotlinArgs.args(request, stdlib == null
                    ? List.of() : List.of(stdlib), plugins);
        } catch (IOException e) {
            return new CompileResult(false, List.of(Problem.error(
                    "java-root-error",
                    "cannot walk java source root : " + e.getMessage())),
                    List.of());
        }
        synchronized (verrou) {
            if (raisonAbandon != null) {
                return new CompileResult(false, List.of(echecNomme(
                        "fork abandonné pour la session — "
                                + raisonAbandon)), List.of());
            }
            List<Integer> echelle = tasAbordables(tasDemandeMo,
                    memoireTotaleMo());
            if (echelle.isEmpty()) {
                return new CompileResult(false, List.of(echecNomme(
                        "la RAM de l'appareil (" + memoireTotaleMo()
                                + " Mo) ne soutient aucun tas de "
                                + "l'échelle — ART réserve "
                                + FACTEUR_RESERVATION + "× le tas")),
                        List.of());
            }
            List<Problem> diagnostiques = new ArrayList<>();
            List<String> codesDeSortie = new ArrayList<>();
            List<Integer> rungsEssayes = new ArrayList<>();
            RuntimeException silence = null;
            Exception cause = null;
            long echeance = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(delaiReponseSec());
            List<Integer> remontee = echelleRemontee(tasDemandeMo,
                    echelle, memoireTotaleMo());
            for (int tentative = 0; tentative < TENTATIVES; tentative++) {
                try {
                    assurerOuvriere(classpath,
                            rungTentative(tentative, echelle, remontee));
                    rungsEssayes.add(rungEffectifMo);
                    ecrireRequete(args);
                    String statut = reponses.lire(echeance);
                    ouvriereEprouvee = true;
                    rungEprouveMo = rungEffectifMo;
                    if (!"OK".equals(statut) && !"ERR".equals(statut)) {
                        throw new IllegalStateException(
                                "réponse inattendue de l'ouvrière : « "
                                        + statut + " »");
                    }
                    boolean succes = "OK".equals(statut);
                    int nombre = Integer.parseInt(
                            reponses.lire(echeance).trim());
                    for (int i = 0; i < nombre; i++) {
                        Problem probleme = lireDiagnostique(
                                reponses.lire(echeance));
                        if (probleme != null) {
                            diagnostiques.add(probleme);
                        }
                    }
                    List<String> ecrites = new ArrayList<>(
                            CompileInventory.written(avant,
                                    CompileInventory.of(
                                            request.outputDir)));
                    return new CompileResult(succes, diagnostiques,
                            ecrites);
                } catch (SilenceOuvriere e) {
                    tuerOuvriere();
                    silence = e;
                    break;
                } catch (IOException | RuntimeException e) {
                    tuerOuvriere();
                    cause = e;
                    if (e instanceof MortOuvriere morte) {
                        codesDeSortie.add(morte.codeTexte());
                    } else if (codesDeSortie.isEmpty()
                            || !dernierCodeEst(codesDeSortie, "-")) {
                        codesDeSortie.add("-");
                    }
                }
            }
            // l'échec est NOMMÉ : les sorties des tentatives, les tas
            // essayés, la queue du stderr — et l'explication du #1748
            // sur les appareils où le JIT est le suspect numéro un
            return new CompileResult(false,
                    List.of(echecFinal(echelle, remontee, rungsEssayes,
                            codesDeSortie, silence, cause)),
                    List.of());
        }
    }

    /** Observation d'essai : l'ouvrière vit-elle encore (la VM se
     *  réutilise entre les lots) ? */
    boolean ouvriereVivante() {
        synchronized (verrou) {
            return ouvriere != null && ouvriere.isAlive();
        }
    }

    /** Le rung de l'échelle pour cette tentative : la première
     *  réutilise l'ouvrière vivante, sinon le dernier rung ÉPROUVÉ,
     *  sinon le haut de l'échelle ; une reprise après une morte SANS
     *  réponse descend d'un rung (un abort de démarrage pointe une
     *  réservation de tas trop grande), une reprise après une morte
     *  APRÈS réponse MONTE d'un rung dans l'échelle de remontée (le
     *  tas essayé était trop petit pour le lot — l'OOM d'ART aborte
     *  sans message, lot XII) ; au sommet de la remontée, rester (le
     *  dernier essai au même tas). */
    private int rungTentative(int tentative, List<Integer> echelle,
                              List<Integer> remontee) {
        if (tentative == 0) {
            if (ouvriere != null && ouvriere.isAlive()) {
                return rungEffectifMo;
            }
            if (rungEprouveMo > 0) {
                return rungEprouveMo;
            }
            return echelle.get(0);
        }
        if (ouvriereEprouvee) {
            for (int rung : remontee) {
                if (rung > rungEffectifMo) {
                    return rung;
                }
            }
            return rungEffectifMo;
        }
        int index = echelle.indexOf(rungEffectifMo);
        return index >= 0 && index + 1 < echelle.size()
                ? echelle.get(index + 1)
                : rungEffectifMo;
    }

    /** L'échelle de REMONTÉE (lot XII) : les rungs qu'une morte APRÈS
     *  réponse peut gravir — le tas essayé était trop petit pour le
     *  lot (l'OOM mid-run d'ART aborte sans message). Union de
     *  l'échelle de démarrage, du tas demandé et du rung 1536, à la
     *  garde ASSOULIE (rung × 2 ≤ 80 % de la RAM), triée ascendante.
     *  RAM inconnue : tout passe, l'appareil reste l'autorité. */
    static List<Integer> echelleRemontee(int demandeMo,
                                         List<Integer> echelleDemarrage,
                                         long totalRamMo) {
        LinkedHashSet<Integer> candidats =
                new LinkedHashSet<>(echelleDemarrage);
        candidats.add(demandeMo);
        candidats.add(RUNG_REMONTEE_SUPPLEMENTAIRE_MO);
        List<Integer> ordonnée = new ArrayList<>(candidats);
        ordonnée.sort(null);
        if (totalRamMo <= 0) {
            return ordonnée;
        }
        long budget = Math.round(totalRamMo * FRACTION_REMONTEE_RAM);
        List<Integer> abordables = new ArrayList<>();
        for (int candidat : ordonnée) {
            if (candidat * FACTEUR_RESERVATION <= budget) {
                abordables.add(candidat);
            }
        }
        return abordables;
    }

    /** Une ligne de diagnostic « SÉVÉRITÉ\tcode\tlieu\tmessage » en
     *  {@code Problem}, ou {@code null} si la ligne est absente. */
    private static Problem lireDiagnostique(String ligne) {
        if (ligne == null) {
            return null;
        }
        String[] champs = ligne.split("\t", -1);
        if (champs.length != 4) {
            return Problem.error("kotlin.worker",
                    "ligne de diagnostic inattendue : « " + ligne + " »");
        }
        boolean erreur = "ERROR".equals(champs[0]);
        Problem probleme = erreur
                ? Problem.error(champs[1], dechapper(champs[3]))
                : Problem.warn(champs[1], dechapper(champs[3]));
        if (!champs[2].isEmpty()) {
            probleme = probleme.at(champs[2]);
        }
        return probleme;
    }

    /** Défait l'échappement de l'ouvrière (\\, \t, \n, \r). */
    static String dechapper(String texte) {
        if (texte == null || texte.indexOf('\\') < 0) {
            return texte;
        }
        StringBuilder out = new StringBuilder(texte.length());
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (c == '\\' && i + 1 < texte.length()) {
                char suivant = texte.charAt(++i);
                switch (suivant) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(suivant);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Démarre l'ouvrière si besoin (elle vit entre les lots) au
     *  rung demandé ; une ouvrière vivante reste à SON rung. */
    private void assurerOuvriere(String classpath, int rungMo)
            throws IOException {
        if (ouvriere != null && ouvriere.isAlive()) {
            return;
        }
        List<String> commande = new ArrayList<>();
        commande.add(binaireVm());
        commande.add("-Xmx" + rungMo + "m");
        // la VM en ligne de commande n'a pas le bac à sable de l'app :
        // tmpdir par défaut inexistant (le compilateur y crée ses
        // fichiers temporaires), couleurs jansi qui tueraient la VM
        // sur un NoClassDefFoundError — la manière CodeAssist
        commande.add("-Djava.io.tmpdir=" + tmpdirOuvriere());
        commande.add("-Dkotlin.colors.enabled=false");
        // les RESSOURCES du pack (les descripteurs d'extensions du
        // registre IntelliJ, les .kotlin_builtins) s'extraitent à
        // côté : PathUtil réécrit (lot XI) les lit par système de
        // fichiers — un chemin par classloader n'existe pas pour un
        // pack de dex
        Path home = homeOuvriere(classpath);
        if (home != null) {
            commande.add("-Djoide.kotlinc.home=" + home);
        }
        if (inliningJitDesactive()) {
            // CodeAssist #1748 : le JIT d'ART (Android ≤ 11) malcompile
            // le code FIR du compilateur quand il inline à travers un
            // cache d'appel polymorphe ; un budget d'inlining nul
            // éteint l'inliner du JIT, les méthodes compilent quand même
            commande.add("-Xcompiler-option");
            commande.add("--inline-max-code-units=0");
        }
        commande.add("-cp");
        commande.add(classpath);
        commande.add(OUVRIER);
        ouvriere = new ProcessBuilder(commande).start();
        entree = new BufferedWriter(new OutputStreamWriter(
                ouvriere.getOutputStream(), StandardCharsets.UTF_8));
        reponses = new FileDeReponse(ouvriere);
        journal = new JournalOuvriere(ouvriere, cheminJournalStderr());
        rungEffectifMo = rungMo;
        ouvriereEprouvee = false;
    }

    /** La requête : nombre d'arguments puis un par ligne — un
     *  argument ne porte JAMAIS de saut de ligne (ce sont des
     *  chemins et des drapeaux). */
    private void ecrireRequete(List<String> args) throws IOException {
        entree.write(Integer.toString(args.size()));
        entree.write('\n');
        for (String argument : args) {
            entree.write(argument);
            entree.write('\n');
        }
        entree.flush();
    }

    /** Tue l'ouvrière (et son journal) — suivie d'un re-fork ou d'un
     *  échec nommé, jamais d'un canal mort réutilisé. La destruction
     *  passe d'abord (les tuyaux se ferment, le drain du stderr se
     *  TERMINE), puis la queue se capture COMPLÈTE : c'est la course
     *  du lot X qui avalait le « Abort message » d'ART sur le
     *  terrain (sortie 134 sans cause visible). */
    private void tuerOuvriere() {
        if (ouvriere != null) {
            ouvriere.destroyForcibly();
        }
        if (journal != null) {
            journal.arreter();
            queueDerniere = journal.queueTexte();
            journal = null;
        }
        if (reponses != null) {
            reponses.arreter();
            reponses = null;
        }
        ouvriere = null;
        entree = null;
    }

    /** L'échec final : NOMMÉ — les sorties des tentatives, les tas
     *  essayés (la remontée y figure), la queue du stderr,
     *  l'explication du #1748 si le JIT de l'appareil est le suspect,
     *  le soupçon d'OOM si la remontée a joué sans guérir, et le
     *  compteur de session. */
    private Problem echecFinal(List<Integer> echelle,
                               List<Integer> remontee,
                               List<Integer> rungsEssayes,
                               List<String> codesDeSortie,
                               RuntimeException silence,
                               Exception cause) {
        String motif;
        if (silence != null) {
            motif = "l'ouvrière n'a pas répondu en " + delaiReponseSec()
                    + " s (tas " + tasEssaye(echelle) + " Mo)";
        } else if (codesDeSortie.isEmpty()) {
            motif = "l'ouvrière a rompu le protocole : "
                    + (cause == null ? "sans cause"
                            : cause.getMessage() == null
                                    ? cause.getClass().getSimpleName()
                                    : cause.getMessage())
                    + " (tas " + tasEssaye(echelle) + " Mo)";
        } else {
            motif = "l'ouvrière est morte aux " + motNombre(
                    codesDeSortie.size())
                    + " tentatives (sortie " + String.join(
                            " puis ", codesDeSortie) + ")"
                    + " — pack " + PacksOutils.KOTLIN + ", binaire "
                    + binaireVm() + ", tas " + tasEssaye(echelle) + " Mo";
        }
        if (rungsEssayes.size() > 1) {
            motif += " ; tas essayés : " + String.join(" puis ",
                    rungsEssayes.stream().map(String::valueOf).toList());
        }
        int sdk = versionSdk();
        boolean jitSuspect = codesDeSortie.size() > 1 && sdk >= 1
                && sdk <= SDK_JIT_CRASHES;
        if (jitSuspect) {
            motif += " — Android " + sdk + " : le JIT ART est connu "
                    + "pour malcompiler le compilateur Kotlin (même "
                    + "signature que CodeAssist #1748) ; l'inlining "
                    + "est déjà désactivé au lancement, réessayer le "
                    + "build, sinon signaler le crash";
        } else if (!codesDeSortie.isEmpty()) {
            motif += " — Android " + (sdk >= 1 ? String.valueOf(sdk)
                    : "?") + (inliningJitDesactive()
                            ? ", inlining JIT désactivé"
                            : ", inlining JIT actif");
        }
        if (rungsEssayes.size() > 1 && ouvriereEprouvee
                && remonteeNonGraviee(remontee, rungsEssayes)) {
            motif += " ; la remontée a plafonné : le lot dépasse "
                    + "peut-être le tas le plus grand que cet appareil "
                    + "(RAM " + memoireTotaleMo() + " Mo) soutient à la "
                    + "garde de remontée (rung × " + FACTEUR_RESERVATION
                    + " ≤ " + FRACTION_REMONTEE_RAM + " × RAM) — "
                    + "rungs de remontée : " + remontee;
        }
        motif += queueTexte(queueDerniere);
        // un échec de plus pour la session — SAUF le crash du JIT :
        // abandonner ne le guérit pas, le prochain lot réessaie une
        // ouvrière neuve (la manière CodeAssist)
        if (!jitSuspect) {
            echecSession(motif);
        }
        return echecNomme(motif);
    }

    /** La remontée a-t-elle atteint son sommet sans guérir ? (le
     *  dernier tas essayé est le plus grand rung de la remontée). */
    private static boolean remonteeNonGraviee(List<Integer> remontee,
                                              List<Integer> essayes) {
        if (remontee.isEmpty() || essayes.isEmpty()) {
            return false;
        }
        return essayes.get(essayes.size() - 1)
                >= remontee.get(remontee.size() - 1);
    }

    /** DEUX, TROIS, ou le chiffre au-delà. */
    private static String motNombre(int nombre) {
        return switch (nombre) {
            case 2 -> "DEUX";
            case 3 -> "TROIS";
            default -> String.valueOf(nombre);
        };
    }

    /** Un échec complet de plus ; au-delà du plafond, les lots
     *  suivants échouent VITE (plus de forcs payés pour rien). */
    private void echecSession(String motif) {
        echecsSession++;
        if (echecsSession >= ECHECS_AVANT_ABANDON
                && raisonAbandon == null) {
            raisonAbandon = motif;
        }
    }

    private static String queueTexte(String queue) {
        return queue.isEmpty()
                ? " ; stderr : vide (un abort natif sans message — le "
                        + "« Fatal signal » d'ART ne va qu'au logcat "
                        + "de l'appareil)"
                : " ; stderr : " + queue;
    }

    /** Le tas effectivement essayé : le rung de la dernière VM
     *  forkée, sinon le haut de l'échelle. */
    private int tasEssaye(List<Integer> echelle) {
        return rungEffectifMo > 0 ? rungEffectifMo : echelle.get(0);
    }

    private static boolean dernierCodeEst(List<String> codes,
                                          String code) {
        return !codes.isEmpty()
                && codes.get(codes.size() - 1).equals(code);
    }

    /** Le tas demandé : la propriété {@code joide.kotlinWorkerHeap}
     *  (« 1g », « 768m »), sinon l'ADR-069 (1 Gio). */
    private static int tasDemande() {
        String propriete = System.getProperty("joide.kotlinWorkerHeap");
        if (propriete != null && !propriete.isBlank()) {
            try {
                return parseTas(propriete);
            } catch (RuntimeException e) {
                // valeur illisible : le défaut tient (une propriété
                // mal écrite ne doit pas casser la compilation)
            }
        }
        return TAS_DEFAUT_MO;
    }

    /** « 1g » / « 768m » / « 512 » → Mo, borné aux plafonds de
     *  l'ouvrière. */
    static int parseTas(String texte) {
        String t = texte.trim().toLowerCase(java.util.Locale.ROOT);
        int facteur = 1;
        if (t.endsWith("g")) {
            facteur = 1024;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            t = t.substring(0, t.length() - 1);
        }
        int valeur = Integer.parseInt(t.trim()) * facteur;
        return Math.max(TAS_MIN_MO, Math.min(TAS_MAX_MO, valeur));
    }

    /** L'échelle de tas : le rung demandé puis les rungs de secours,
     *  dédoublonnés, MOINS ceux dont la réservation dépasse la moitié
     *  de la RAM de l'appareil (RAM inconnue : tout passe, l'appareil
     *  reste l'autorité). */
    static List<Integer> tasAbordables(int demandeMo, long totalRamMo) {
        LinkedHashSet<Integer> candidats = new LinkedHashSet<>();
        candidats.add(demandeMo);
        for (int rung : ECHELLE_SECOURS_MO) {
            candidats.add(rung);
        }
        List<Integer> liste = new ArrayList<>(candidats);
        if (totalRamMo <= 0) {
            return liste;
        }
        long budget = Math.round(totalRamMo * FRACTION_BUDGET_RAM);
        List<Integer> abordables = new ArrayList<>();
        for (int candidat : liste) {
            if (candidat * FACTEUR_RESERVATION <= budget) {
                abordables.add(candidat);
            }
        }
        return abordables;
    }

    /** La RAM totale de l'appareil (Mo), par {@code /proc/meminfo}
     *  — 0 si elle ne se lit pas (le bureau sans {@code /proc},
     *  un appareil restreint) : l'échelle reste alors entière. */
    static long memoireTotaleMo() {
        Path meminfo = Path.of("/proc/meminfo");
        if (!Files.isRegularFile(meminfo)) {
            return 0L;
        }
        try (BufferedReader lecteur = Files.newBufferedReader(meminfo,
                StandardCharsets.UTF_8)) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                if (ligne.startsWith("MemTotal:")) {
                    String kb = ligne.substring("MemTotal:".length())
                            .trim().split(" ")[0].trim();
                    return Long.parseLong(kb) / 1024L;
                }
            }
        } catch (IOException | NumberFormatException e) {
            return 0L;
        }
        return 0L;
    }

    /** Le délai d'une réponse (propriété
     *  {@code joide.kotlinWorkerTimeoutSec}, défaut 3600 s). */
    private static long delaiReponseSec() {
        String propriete = System.getProperty(
                "joide.kotlinWorkerTimeoutSec");
        if (propriete != null && !propriete.isBlank()) {
            try {
                return Math.max(1, Long.parseLong(propriete.trim()));
            } catch (NumberFormatException e) {
                // valeur illisible : le défaut tient
            }
        }
        return DELAI_REPONSE_DEFAUT_SEC;
    }

    /** Le SDK Android du processus (réflexion sur
     *  {@code android.os.Build$VERSION} — le module n'a PAS le SDK
     *  à la compilation mais le processus de l'app l'a au runtime),
     *  sinon {@code /system/build.prop}, sinon -1 (bureau). */
    static int versionSdk() {
        try {
            return Class.forName("android.os.Build$VERSION")
                    .getField("SDK_INT").getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return sdkDepuisBuildProp();
        }
    }

    private static int sdkDepuisBuildProp() {
        Path prop = Path.of("/system/build.prop");
        if (!Files.isRegularFile(prop)) {
            return -1;
        }
        try (BufferedReader lecteur = Files.newBufferedReader(prop,
                StandardCharsets.UTF_8)) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                if (ligne.startsWith("ro.build.version.sdk=")) {
                    return Integer.parseInt(ligne.substring(
                            "ro.build.version.sdk=".length()).trim());
                }
            }
        } catch (IOException | NumberFormatException e) {
            // SDK inconnu : -1
        }
        return -1;
    }

    /** Vrai quand le drapeau d'inlining nul doit partir : SDK connu
     *  ≤ 30 (Android 11 et plus ancien — le JIT y malcompile le
     *  compilateur), ou SDK inconnu avec un binaire dalvikvm (on est
     *  sur Android : la prudence prime, le drapeau ne coûte que la
     *  vitesse du JIT). Sur le bureau (java), JAMAIS — un drapeau
     *  inconnu tuerait la VM au démarrage. */
    static boolean inliningJitDesactive() {
        int sdk = versionSdk();
        if (sdk >= 1) {
            return sdk <= SDK_JIT_CRASHES;
        }
        return binaireVm().contains("dalvikvm");
    }

    /** Le tmpdir de l'ouvrière : {@code kotlinc-tmp} du répertoire
     *  d'outillage (le compilateur y crée ses fichiers temporaires) ;
     *  s'il ne se crée pas, le tmpdir du parent. */
    private static String tmpdirOuvriere() throws IOException {
        Path tmp = ToolchainLocator.defaultCacheDir()
                .resolve("kotlinc-tmp");
        try {
            Files.createDirectories(tmp);
        } catch (IOException e) {
            return System.getProperty("java.io.tmpdir", "/tmp");
        }
        return tmp.toString();
    }

    /** Le {@code joide.kotlinc.home} de l'ouvrière : les RESSOURCES
     *  du pack (tout sauf les dex) extraites UNE fois dans {@code
     *  kotlin-home} du répertoire d'outillage, la garde étant le
     *  SHA-256 épinglé du pack — null pour l'essai bureau (les jars
     *  portent leurs ressources par classloader). */
    static Path homeOuvriere(String classpath) {
        if (!classpath.endsWith(".dex.zip")) {
            return null;
        }
        Path home = ToolchainLocator.defaultCacheDir()
                .resolve("kotlin-home");
        Path marqueur = ToolchainLocator.defaultCacheDir()
                .resolve("kotlin-home.sha");
        try {
            if (Files.isRegularFile(marqueur)
                    && Files.isDirectory(home)
                    && PacksOutils.KOTLIN.sha256.equals(
                            Files.readString(marqueur).trim())) {
                return home;
            }
            Files.createDirectories(home);
            try (java.util.zip.ZipFile pack = new java.util.zip.ZipFile(
                    Path.of(classpath).toFile())) {
                var entrées = pack.entries();
                while (entrées.hasMoreElements()) {
                    java.util.zip.ZipEntry entrée = entrées
                            .nextElement();
                    if (entrée.isDirectory()
                            || entrée.getName().endsWith(".dex")) {
                        continue;
                    }
                    Path cible = home.resolve(entrée.getName());
                    if (!cible.normalize().startsWith(home)) {
                        continue;   // slip de zip : entrée rejetée
                    }
                    Files.createDirectories(cible.getParent());
                    try (var source = pack.getInputStream(entrée)) {
                        Files.copy(source, cible,
                                java.nio.file.StandardCopyOption
                                        .REPLACE_EXISTING);
                    }
                }
            }
            Files.writeString(marqueur, PacksOutils.KOTLIN.sha256);
            return home;
        } catch (IOException | RuntimeException e) {
            // sans home, PathUtil réécrit dégrade proprement (la
            // valeur par défaut manque de descripteurs) — le lot
            // échouera nommé si le registre en a besoin
            return null;
        }
    }

    /** Le jar ORIGINAL de la bibliothèque standard, extrait du pack
     *  (ressource {@code jars/kotlin-stdlib-<v>.jar}, lot XVI) : le
     *  classpath de compilation du compilateur exige des jar RÉELS —
     *  son JAR FS ne lit pas les dex du pack, et sur l'appareil il
     *  n'existe AUCUN {@code kotlin-stdlib.jar} sur disque (la
     *  manière CodeAssist : le jar réel téléchargé ; ici le pack le
     *  porte). {@code null} hors pack (les essais bureau portent la
     *  stdlib dans le classpath de la demande — {@code
     *  joide.kotlinWorkerCp} n'est pas un {@code .dex.zip}). */
    static Path jarStdlib(Path home) throws IOException {
        if (home == null) {
            return null;
        }
        Path jars = home.resolve("jars");
        if (!Files.isDirectory(jars)) {
            return null;
        }
        try (var flux = Files.list(jars)) {
            return flux.filter(p -> {
                        String nom = p.getFileName().toString();
                        return nom.startsWith("kotlin-stdlib-")
                                && nom.endsWith(".jar");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }
    }

    /** Le journal stderr de l'ouvrière (écrasé à chaque fork). */
    static Path cheminJournalStderr() {
        return ToolchainLocator.defaultCacheDir()
                .resolve("ouvriere-kotlin.err");
    }

    /** Le binaire de la VM : surcharge d'essai, puis l'ART de
     *  l'appareil, puis « java » (le bureau sans surcharge échouera
     *  proprement au premier fork — le pilote embarqué prime là-bas). */
    private static String binaireVm() {
        String surcharge = System.getProperty("joide.kotlinVm");
        if (surcharge != null && !surcharge.isBlank()) {
            return surcharge;
        }
        for (String candidat : BINAIRES_VM) {
            if (Files.isRegularFile(Path.of(candidat))) {
                return candidat;
            }
        }
        return "java";
    }

    /** Le classpath de l'ouvrière : surcharge d'essai, sinon le pack
     *  différé épinglé (téléchargé une première fois si absent). */
    private static String classpathOuvriere() throws IOException {
        String surcharge = System.getProperty("joide.kotlinWorkerCp");
        if (surcharge != null && !surcharge.isBlank()) {
            return surcharge;
        }
        return PacksOutils.fichierAssure(PacksOutils.KOTLIN).toString();
    }

    private static Problem echecNomme(String motif) {
        return Problem.error("kotlin.worker",
                "la VM ouvrière du compilateur Kotlin a échoué : "
                        + motif);
    }

    /** La sortie standard de l'ouvrière lue en FIL SÉPARÉ vers une
     *  file bornée : une morte est vue À L'EOF (jamais pendue sur un
     *  canal mort), un silence est borné par l'échéance du lot, et
     *  le fil du protocole ne bloque JAMAIS sur un readLine sans
     *  horizon. */
    private static final class FileDeReponse {

        /** Le sentinelle poussée à l'EOF : l'ouvrière est morte. */
        private static final String FIN = "\u0000fin-du-flux";

        private final Process processus;
        private final BufferedReader lecteur;
        private final LinkedBlockingQueue<String> file
                = new LinkedBlockingQueue<>();
        private final Thread fil;

        FileDeReponse(Process processus) {
            this.processus = processus;
            this.lecteur = new BufferedReader(new InputStreamReader(
                    processus.getInputStream(), StandardCharsets.UTF_8));
            this.fil = new Thread(() -> {
                try {
                    while (true) {
                        String ligne = lecteur.readLine();
                        if (ligne == null) {
                            pousser(FIN);
                            return;
                        }
                        pousser(ligne);
                    }
                } catch (IOException lue) {
                    pousser(FIN);
                }
            }, "joide-ouvriere-kotlin-protocol");
            fil.setDaemon(true);
            fil.start();
        }

        /** Pousse une ligne dans la file, sans jamais mourir d'une
         *  interruption (le sentinelle FIN doit toujours passer). */
        private void pousser(String ligne) {
            try {
                file.put(ligne);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** La ligne suivante avant l'échéance — ou l'exception qui
         *  nomme le silence (délai) ou la morte (EOF). */
        String lire(long echeanceNano)
                throws SilenceOuvriere, MortOuvriere {
            while (true) {
                long reste = echeanceNano - System.nanoTime();
                if (reste <= 0) {
                    throw new SilenceOuvriere();
                }
                try {
                    String ligne = file.poll(reste,
                            TimeUnit.NANOSECONDS);
                    if (ligne == null) {
                        throw new SilenceOuvriere();
                    }
                    if (FIN.equals(ligne)) {
                        throw new MortOuvriere(codeSortie());
                    }
                    return ligne;
                } catch (InterruptedException interrompue) {
                    Thread.currentThread().interrupt();
                    throw new MortOuvriere("?");
                }
            }
        }

        /** Le code de sortie d'une ouvrière morte (un SIGABRT d'ART
         *  se voit 134) — « ? » si la VM ne meurt pas dans le délai. */
        private String codeSortie() {
            try {
                if (processus.waitFor(5, TimeUnit.SECONDS)) {
                    return Integer.toString(processus.exitValue());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            processus.destroyForcibly();
            return "?";
        }

        void arreter() {
            fil.interrupt();
            try {
                lecteur.close();
            } catch (IOException e) {
                // l'ouvrière meurt de toute façon avec le processus
            }
        }
    }

    /** Le stderr de l'ouvrière, drainé en FIL SÉPARÉ (une queue non
     *  lue bloque la VM quand le tampon du tuyau se remplit) : le
     *  journal complet va dans un fichier, la queue (40 lignes)
     *  sert à nommer une morte dans la console du build. */
    private static final class JournalOuvriere {

        private final BufferedReader lecteur;
        private final Writer fichier;
        private final Deque<String> queue = new ArrayDeque<>();
        private final Thread fil;

        JournalOuvriere(Process processus, Path chemin) {
            lecteur = new BufferedReader(new InputStreamReader(
                    processus.getErrorStream(), StandardCharsets.UTF_8));
            Writer cible = null;
            try {
                cible = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(chemin.toFile()),
                        StandardCharsets.UTF_8));
            } catch (IOException e) {
                // pas de fichier : la queue mémoire suffit au nommage
            }
            fichier = cible;
            fil = new Thread(() -> {
                try {
                    while (true) {
                        String ligne = lecteur.readLine();
                        if (ligne == null) {
                            return;
                        }
                        synchronized (queue) {
                            queue.addLast(ligne);
                            if (queue.size() > LIGNES_QUEUE_STDERR) {
                                queue.pollFirst();
                            }
                        }
                        if (fichier != null) {
                            try {
                                fichier.write(ligne);
                                fichier.write('\n');
                                fichier.flush();
                            } catch (IOException ecriture) {
                                // le journal ne meurt jamais une compile
                            }
                        }
                    }
                } catch (IOException lue) {
                    // EOF : l'ouvrière est morte, la queue tient
                }
            }, "joide-ouvriere-kotlin-stderr");
            fil.setDaemon(true);
            fil.start();
        }

        /** La queue de stderr en une ligne « l1 | l2 | … », bornée —
         *  « Abort message: … » d'ART atteint la console du build. */
        String queueTexte() {
            synchronized (queue) {
                if (queue.isEmpty()) {
                    return "";
                }
                StringBuilder out = new StringBuilder();
                for (String ligne : queue) {
                    if (out.length() > 0) {
                        out.append(" | ");
                    }
                    out.append(ligne.length() > 160
                            ? ligne.substring(0, 160) + "…"
                            : ligne);
                }
                return out.toString();
            }
        }

        void arreter() {
            // le drain se TERMINE d'abord (l'EOF suit la morte : les
            // dernières lignes entrent dans la queue), la fermeture
            // du lecteur ne les perd plus
            try {
                fil.join(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                lecteur.close();
            } catch (IOException e) {
                // la fin du flux termine le fil
            }
            if (fichier != null) {
                try {
                    fichier.close();
                } catch (IOException e) {
                    // le fichier partiel garde ce qui est écrit
                }
            }
        }
    }

    /** Une ouvrière dont le flux de réponses est arrivé à l'EOF —
     *  un crash, l'OS, un abort : jamais réutilisée. */
    private static final class MortOuvriere
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String code;

        MortOuvriere(String code) {
            super("l'ouvrière Kotlin est morte (sortie " + code + ")");
            this.code = code;
        }

        String codeTexte() {
            return code;
        }
    }

    /** Une ouvrière vivante mais muette au-delà de l'échéance du
     *  lot — le lot échoue nommé plutôt que de pendre le build. */
    private static final class SilenceOuvriere
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SilenceOuvriere() {
            super("aucune réponse dans le délai imparti");
        }
    }
}
