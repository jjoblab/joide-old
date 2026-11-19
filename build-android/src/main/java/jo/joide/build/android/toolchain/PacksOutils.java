package jo.joide.build.android.toolchain;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;
import jo.joide.deps.repo.HttpTransport;
import jo.joide.deps.repo.Transport;
import jo.joide.deps.repo.TransportResponse;
import jo.joide.platform.Hashing;

/**
 * Les packs d'outils DIFFÉRÉS (APK allégé, la manière éprouvée) :
 * D8/R8 et bundletool sont des jars Java — ART ne charge pas des
 * fichiers {@code .class}, ils partent en packs de DEX pré-construits
 * ({@code r8.dex.zip} chez l'IDE de référence) que l'application télécharge
 * UNE fois à la première construction, épinglés par taille et
 * SHA-256 dans le répertoire d'outillage partagé.
 *
 * <p>Le pack se charge par un chargeur dédié ({@link
 * ChargeurClassesOutil}, découvert par ServiceLoader) dont le parent
 * ne porte que les interfaces SPI du pipeline : le mur de chargeurs
 * reste étanche, aucun type d'outil ne traverse le SPI. Tant que les
 * jars des outils vivent encore dans l'APK (construction de
 * transition), les classes embarquées priment — le pack ne sert que
 * le jour où l'APK s'allège de ces jars. Le compilateur Kotlin fait
 * exception (lot VII) : jamais chargé DANS l'app — sa VM FORKÉE
 * (KotlinForkedDriver) ne prend que le FICHIER du pack en
 * classpath.</p>
 *
 * <p>Les empreintes ci-dessous sont produites par la tâche
 * {@code :app-android:dexPacksOutils} (packs déterministes : dex D8
 * + horodatages d'archive fixes) et publiées avec l'application —
 * une divergence au régénération échoue la tâche, jamais un pack non
 * vérifié sur le disque.</p>
 */
public final class PacksOutils {

    /** Base des URL de téléchargement des packs (surchargeable pour
     *  les essais) ; défaut : la release GitHub « toolchain-v1 ». */
    public static final String BASE_URL_DEFAUT
            = "https://github.com/jjoblab/joide/releases/download"
                    + "/toolchain-v1";

    /** Taille attendue du pack D8/R8 (octets). */
    static final long TAILLE_R8 = 4_810_650L;

    /** SHA-256 attendu du pack D8/R8. */
    static final String SHA_R8
            = "756614a3cca7c9b34894fe802bb14edbc4b67dfc170ee90f682ea752d98cb18a";

    /** Taille attendue du pack bundletool complet (octets) — LOT
     *  XXI/b puis LOT XXII : le pack 1.18.3.1 (fabrication du script
     *  seul) portait bundletool + toutes ses dépendances runtime MAIS
     *  PAS l'adaptateur jo/joide/build/android/tools/BundletoolAssembler
     *  — le lot VIII l'a retiré de l'APK précisément parce que le pack
     *  doit le porter : le terrain du 2026-11-22, « embedded build
     *  tool unavailable: BundletoolAssembler — ClassNotFoundException »,
     *  le bouton build mort. Règle dorée tenue (un artefact publié ne
     *  change JAMAIS) : le pack corrigé vit dans le NOUVEAU fichier
     *  versionné bundletool-1.18.3.2.dex.zip (les .1 et .0 restent en
     *  place), bundletool + guava 32.0.1 + protobuf-java 3.22.3 +
     *  protobuf-java-util 3.22.3 + failureaccess + listenablefuture +
     *  jsr305 + error_prone_annotations + checker-qual + L'ADAPTATEUR,
     *  tout dexé ENSEMBLE par le D8 du jar r8 du catalogue (le même
     *  geste que la tâche :app-android:dexPacksOutils, qui reproduit
     *  l'artefact octet pour octet). */
    static final long TAILLE_BUNDLETOOL = 5_655_184L;

    /** SHA-256 attendu du pack bundletool complet. */
    static final String SHA_BUNDLETOOL
            = "f5b09784a56d4b5552d8dddf999389ddd77440a218d7b39e4d740f27d9d5300a";

    /** Taille attendue du pack du compilateur Kotlin (octets) —
     *  le pack ART-patché du lot XI, la PILE STAX du lot XV (l'API
     *  javax.xml.stream + aalto unshadé + stax2-api : ART n'en livre
     *  pas), le JAR ORIGINAL de la stdlib en ressource du lot XVI
     *  (le classpath de compile exige un jar RÉEL — la manière
     *  l'IDE de référence), régénéré contre android.jar 35. LOT XX : ce pack
     *  est IMMUTABLE — le lot XIX l'avait muté (plugin Compose dexé)
     *  sans que le fichier de la release fût remplacé : « pack size
     *  mismatch » sur l'appareil. Règle dorée (l'IDE de référence ne publie
     *  RIEN — audit du 2026-11-17) : un artefact publié ne change
     *  JAMAIS — tout ajout vit dans l'APK ou dans un NOUVEAU fichier
     *  versionné. */
    static final long TAILLE_KOTLIN = 20_989_461L;

    /** SHA-256 attendu du pack du compilateur Kotlin. */
    static final String SHA_KOTLIN
            = "9a9f48fce9f7b57e62522ca66312ffc130abc5fe01103149ebac193e3ee24f14";

    /** Un pack d'outil : fichier, empreinte et URL épinglées. */
    public static final class Pack {

        /** Nom court (« r8 », « bundletool »). */
        public final String nom;
        /** Version de l'outil (celle du catalogue de versions). */
        public final String version;
        /** Nom du fichier dans le cache. */
        public final String fichier;
        /** Taille attendue (octets) — une divergence est un échec. */
        public final long taille;
        /** SHA-256 attendu — une divergence est un échec. */
        public final String sha256;
        /** URL complète du téléchargement épinglé. */
        public final String url;

        Pack(String nom, String version, String fichier,
             long taille, String sha256, String url) {
            this.nom = nom;
            this.version = version;
            this.fichier = fichier;
            this.taille = taille;
            this.sha256 = sha256;
            this.url = url;
        }

        @Override
        public String toString() {
            return nom + " " + version + " (" + fichier + ")";
        }
    }

    /** Le pack D8/R8 (adaptateur InProcessDexer inclus). */
    public static final Pack R8 = new Pack("r8",
            ToolchainLocator.R8_VERSION,
            "r8-" + ToolchainLocator.R8_VERSION + ".dex.zip",
            TAILLE_R8, SHA_R8, BASE_URL_DEFAUT + "/r8-"
                    + ToolchainLocator.R8_VERSION + ".dex.zip");

    /** Le pack bundletool complet (adaptateur BundletoolAssembler
     *  inclus ; LOT XXI/b pour les dépendances runtime du POM, LOT
     *  XXII pour l'adaptateur lui-même : bundletool + guava 32.0.1 +
     *  protobuf-java 3.22.3 + protobuf-java-util 3.22.3 +
     *  failureaccess + listenablefuture + jsr305 +
     *  error_prone_annotations + checker-qual, toutes dexées dans le
     *  pack AVEC l'adaptateur — le BuildRunner charge la classe PAR
     *  NOM dans le chargeur du pack, jamais par ServiceLoader). */
    public static final Pack BUNDLETOOL = new Pack("bundletool",
            "1.18.3", "bundletool-1.18.3.2.dex.zip",
            TAILLE_BUNDLETOOL, SHA_BUNDLETOOL,
            BASE_URL_DEFAUT + "/bundletool-1.18.3.2.dex.zip");

    /** Le pack du compilateur Kotlin (lots VII + XI — VM forkée,
     *  ART-patché) : compilateur 2.3.0 réécrit par les sept passes
     *  du patcheur ART + stdlib + reflect + script-runtime +
     *  daemon-embeddable + coroutines + les SHIMS ART + l'ouvrier
     *  KotlinWorkerMain, avec les RESSOURCES du jar (les .kotlin_module
     *  notamment) que le dexage seul ne transporte pas. */
    public static final Pack KOTLIN = new Pack("kotlin",
            "2.3.0", "kotlin-compiler-2.3.0.dex.zip",
            TAILLE_KOTLIN, SHA_KOTLIN,
            BASE_URL_DEFAUT + "/kotlin-compiler-2.3.0.dex.zip");

    /** Les chargeurs déjà ouverts, par nom de pack. */
    private static final Map<String, ClassLoader> CHARGEURS
            = new ConcurrentHashMap<>();

    private PacksOutils() {
    }

    /**
     * @param spi l'interface d'outil cherchée
     * @return le pack qui la porte, ou {@code null} (apksig reste
     *         embarqué — trop léger pour un pack)
     */
    public static Pack packDe(Class<?> spi) {
        if (spi == Dexer.class) {
            return R8;
        }
        if (spi == BundleAssembler.class) {
            return BUNDLETOOL;
        }
        return null;
    }

    /**
     * Le fichier local d'un pack, ASSURÉ présent et sain : le pack
     * absent ou corrompu se télécharge une première fois (épinglé par
     * taille et SHA-256) puis retourne son chemin. Le pilote de la
     * VM forkée (KotlinForkedDriver) n'a pas besoin du chargeur de
     * classes — seulement du FICHIER, passé en classpath de la VM
     * ouvrière.
     *
     * <p>Le fichier retourné est TOUJOURS en LECTURE SEULE (lot XIII) :
     * ART refuse de charger un dex/jar MODIFIABLE par un chargeur de
     * classes (la politique W^X d'Android 10+, renforcée sur Android
     * 14+ — la manière éprouvée, {@code ArtKotlinPluginLoader} :
     * « Android 14+ won't load a writable dex/jar through a class
     * loader »). Sans cela, la VM ouvrière forkée abortait au
     * démarrage — {@code SecurityException: Writable dex file … is
     * not allowed} puis {@code Check failed: system_class_loader !=
     * nullptr}, la sortie 134 sans stderr du terrain. L'imposition est
     * IDEMPOTENTE et couvre les packs déjà sur le disque (posés par
     * une version antérieure, jamais re-téléchargés : taille et
     * SHA-256 sains).</p>
     *
     * @param pack le pack visé
     * @return le chemin local vérifié, en lecture seule
     * @throws IOException si le téléchargement échoue ou l'empreinte
     *                     diverge
     */
    public static Path fichierAssure(Pack pack) throws IOException {
        Path cible = fichierLocal(pack);
        if (!sain(cible, pack)) {
            telechargePinned(pack, cible);
        }
        imposerLectureSeule(cible);
        return cible;
    }

    /** Le fichier passe en LECTURE SEULE — au mieux : jamais un
     *  échec dur si le système de fichiers refuse (le chargeur en
     *  processus s'en accommode, la même tolérance que
     *  ChargeurDexAndroid). */
    static void imposerLectureSeule(Path fichier) {
        try {
            java.io.File f = fichier.toFile();
            if (f.canWrite()) {
                f.setReadOnly();
            }
        } catch (RuntimeException ignore) {
            // au mieux : ART ne réécrit pas le pack d'entrée
        }
    }

    /**
     * Le fichier local d'un pack dans le répertoire d'outillage
     * (présent ou non — aucun téléchargement ici).
     *
     * @param pack le pack visé
     * @return son chemin dans le cache
     */
    public static Path fichierLocal(Pack pack) {
        return ToolchainLocator.defaultCacheDir()
                .resolve("packs").resolve(pack.fichier);
    }

    /**
     * Le pack est-il présent et SAIN sur le disque (taille et
     * empreinte) ? Aucun téléchargement, aucune écriture.
     *
     * @param pack le pack visé
     * @return vrai si le fichier local vérifie l'épinglage
     */
    public static boolean present(Pack pack) {
        return sain(fichierLocal(pack), pack);
    }

    /**
     * Le chargeur d'un pack : télécharge le pack une première fois
     * (épinglé par taille et SHA-256, transport HTTPS du résolveur)
     * puis l'ouvre par le pont Android découvert sur le classpath.
     * Le chargeur est Mémoïsé : un pack s'ouvre une fois par machine
     * virtuelle.
     *
     * @param pack le pack visé
     * @return le chargeur du pack
     * @throws IOException si le téléchargement échoue, l'empreinte
     *                     diverge, ou qu'aucun pont Android n'est
     *                     disponible (bureau : les outils restent sur
     *                     le classpath)
     */
    public static ClassLoader chargeurDe(Pack pack) throws IOException {
        ClassLoader existant = CHARGEURS.get(pack.nom);
        if (existant != null) {
            return existant;
        }
        Path cible = fichierAssure(pack);
        ChargeurClassesOutil pont = ServiceLoader
                .load(ChargeurClassesOutil.class).findFirst()
                .orElse(null);
        if (pont == null) {
            throw new IOException("no dex pack loader on classpath ("
                    + pack + ") — packs are an on-device route");
        }
        ClassLoader chargeur = pont.charger(cible,
                ChargeurClassesOutil.class.getClassLoader());
        CHARGEURS.put(pack.nom, chargeur);
        return chargeur;
    }

    /** Le fichier local vérifie-t-il taille et empreinte ? */
    private static boolean sain(Path fichier, Pack pack) {
        try {
            if (!Files.isRegularFile(fichier)
                    || Files.size(fichier) != pack.taille) {
                return false;
            }
            return Hashing.toHex(Hashing.sha256(fichier))
                    .equalsIgnoreCase(pack.sha256);
        } catch (IOException e) {
            // illisible : absent au sens du téléchargement, jamais un
            // crash de construction sur un cache corrompu
            return false;
        }
    }

    /**
     * Téléchargement épinglé : écriture en staging, vérification
     * taille + SHA-256 AVANT le posage atomique — jamais un binaire
     * non vérifié au chemin final. Le staging passe en LECTURE SEULE
     * AVANT le move (la manière éprouvée, {@code
     * ArtKotlinPluginLoader} : chmod le temporaire puis move
     * atomique) — le fichier final naît en lecture seule, ART le
     * charge sans l'objection W^X ; une cible déjà posée (un pack
     * corrompu en lecture seule d'une session antérieure) se SUPPRIME
     * d'abord — le fichier est ré-écrit, pas ré-ouvert en écriture.
     */
    private static void telechargePinned(Pack pack, Path cible)
            throws IOException {
        Files.createDirectories(cible.getParent());
        Path staging = cible.resolveSibling(
                cible.getFileName() + ".part-" + System.nanoTime());
        try {
            Transport transport = new HttpTransport();
            TransportResponse reponse = transport.get(pack.url, null);
            if (!reponse.isHit()) {
                throw new IOException("pack download failed for "
                        + pack.nom + " : " + reponse.message
                        + " (" + pack.url + ")");
            }
            if (reponse.bytes.length != pack.taille) {
                throw new IOException("pack size mismatch for "
                        + pack.nom + " : " + reponse.bytes.length
                        + " octets, attendus " + pack.taille);
            }
            String empreinte = Hashing.toHex(
                    Hashing.sha256(reponse.bytes));
            if (!empreinte.equalsIgnoreCase(pack.sha256)) {
                throw new IOException("pack checksum mismatch for "
                        + pack.nom + " : " + empreinte
                        + " (attendu " + pack.sha256 + ")");
            }
            try (OutputStream sortie = Files.newOutputStream(staging)) {
                sortie.write(reponse.bytes);
            }
            imposerLectureSeule(staging);
            Files.deleteIfExists(cible);
            try {
                Files.move(staging, cible, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException e) {
                Files.move(staging, cible,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // le staging résiduel (un move raté) se nettoie : la
            // lecture seule ne doit pas garer un fichier orphelin
            try {
                java.io.File reste = staging.toFile();
                reste.setWritable(true);
                Files.deleteIfExists(staging);
            } catch (IOException | RuntimeException ignore) {
                // au mieux : le .part-* sera écrasé à la prochaine
            }
        }
    }
}
