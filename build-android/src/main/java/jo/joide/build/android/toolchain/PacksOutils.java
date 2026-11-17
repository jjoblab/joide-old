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
 * Les packs d'outils DIFFÉRÉS (APK allégé, la manière CodeAssist) :
 * D8/R8 et bundletool sont des jars Java — ART ne charge pas des
 * fichiers {@code .class}, ils partent en packs de DEX pré-construits
 * ({@code r8.dex.zip} chez CodeAssist) que l'application télécharge
 * UNE fois à la première construction, épinglés par taille et
 * SHA-256 dans le répertoire d'outillage partagé.
 *
 * <p>Le pack se charge par un chargeur dédié ({@link
 * ChargeurClassesOutil}, découvert par ServiceLoader) dont le parent
 * ne porte que les interfaces SPI du pipeline : le mur de chargeurs
 * reste étanche, aucun type d'outil ne traverse le SPI. Tant que les
 * jars des outils vivent encore dans l'APK (construction de
 * transition), les classes embarquées priment — le pack ne sert que
 * le jour où l'APK s'allège de ces jars.</p>
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
    static final long TAILLE_R8 = 4_810_697L;

    /** SHA-256 attendu du pack D8/R8. */
    static final String SHA_R8
            = "381fab0e5ec0e8b60e3aed3e656b53ce677f90c905691f7e4fe423fbe5ee5ccf";

    /** Taille attendue du pack bundletool+guava (octets). */
    static final long TAILLE_BUNDLETOOL = 5_039_954L;

    /** SHA-256 attendu du pack bundletool+guava. */
    static final String SHA_BUNDLETOOL
            = "f02b5d5ccbad6d01725a69e288ed5407b48814c65d2b1bfac9c47c8dd69162e8";

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

    /** Le pack bundletool + guava (adaptateur BundletoolAssembler
     *  inclus). */
    public static final Pack BUNDLETOOL = new Pack("bundletool",
            "1.18.3", "bundletool-1.18.3.dex.zip",
            TAILLE_BUNDLETOOL, SHA_BUNDLETOOL,
            BASE_URL_DEFAUT + "/bundletool-1.18.3.dex.zip");

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
        Path cible = fichierLocal(pack);
        if (!sain(cible, pack)) {
            telechargePinned(pack, cible);
        }
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
     * non vérifié au chemin final.
     */
    private static void telechargePinned(Pack pack, Path cible)
            throws IOException {
        Files.createDirectories(cible.getParent());
        Path staging = cible.resolveSibling(
                cible.getFileName() + ".part-" + System.nanoTime());
        Transport transport = new HttpTransport();
        TransportResponse reponse = transport.get(pack.url, null);
        if (!reponse.isHit()) {
            throw new IOException("pack download failed for "
                    + pack.nom + " : " + reponse.message
                    + " (" + pack.url + ")");
        }
        if (reponse.bytes.length != pack.taille) {
            throw new IOException("pack size mismatch for " + pack.nom
                    + " : " + reponse.bytes.length
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
        try {
            Files.move(staging, cible, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            Files.move(staging, cible,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
