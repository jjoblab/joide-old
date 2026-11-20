package jo.joide.build.android.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import jo.joide.build.android.sign.ApkSignerService;

/**
 * Signature et vérification d'APK par apksig, en processus. Les clés
 * sont lues d'un fichier de clés JKS/PKCS12 — jamais par détection
 * automatique de format (lot C11 : le lecteur à sonde du JDK exige
 * qu'un fournisseur installé charge le fichier, or l'appareil
 * n'installe que PKCS12 et BKS, et son lecteur PKCS12 ne déchiffre
 * pas un PKCS12 moderne — PBES2, la manière d'écriture de keytool
 * depuis Java 12 ; la sonde ne reconnaissait alors rien et le
 * terrain rendait « Unrecognized keystore format, please load with
 * a specific type » sans nommer le remède). Le magasin s'ouvre donc
 * par TYPE EXPLICITE, dans l'ordre : PKCS12, JKS, BKS du lecteur
 * installé — chaque type absent sur la machine ne fait qu'une panne
 * notée — puis PKCS12 et BKS du fournisseur cryptographique chargé
 * par réflexion, présent sur l'appareil, qui sait déchiffrer le
 * PKCS12 moderne là où le lecteur installé ne peut pas. Les schémas
 * activés restent explicites — jamais de signature silencieuse d'un
 * schéma que la variante n'a pas demandée.
 */
public final class ApksigSigner implements ApkSignerService {

    /** Version d'apksig, alignée sur le catalogue de versions du dépôt. */
    public static final String APKSIG_VERSION = "9.5.1";

    /**
     * Le fournisseur cryptographique complémentaire, chargé par
     * REFLEXION : présent sur l'appareil (classpath de démarrage),
     * absent du classpath de test de la machine virtuelle — aucun
     * répertoire d'artefacts ne doit l'exiger, et son absence ne
     * casse rien (la chaîne de types s'arrête aux lecteurs installés).
     * C'est lui qui déchiffre un PKCS12 moderne (PBES2) quand le
     * lecteur installé de l'appareil ne le peut pas.
     */
    private static final Provider FOURNISSEUR_COMPLEMENTAIRE =
            chargerFournisseurComplementaire();

    private static Provider chargerFournisseurComplementaire() {
        try {
            Class<?> classe = Class.forName(
                    "org.bouncycastle.jce.provider.BouncyCastleProvider");
            return (Provider) classe.getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException | RuntimeException
                | LinkageError echoue) {
            return null;
        }
    }

    private final String version;

    /** Enregistrement ServiceLoader : version du catalogue. */
    public ApksigSigner() {
        this(APKSIG_VERSION);
    }

    /** @param version libellé de version pour l'empreinte des tâches */
    public ApksigSigner(String version) {
        this.version = "apksig:" + version;
    }

    @Override
    public void sign(SigningRequest request) throws IOException {
        try {
            KeyStore store = ouvrirMagasin(request.keystore,
                    request.keystorePassword);
            PrivateKey key = cle(store, request.alias,
                    request.keyPassword, request.keystorePassword);
            List<X509Certificate> chain = chaine(store, request.alias);
            if (chain.isEmpty()) {
                throw new IOException("certificate not found for alias: "
                        + request.alias);
            }
            ApkSigner.SignerConfig config = new ApkSigner.SignerConfig
                    .Builder("joide",
                            new com.android.apksig.KeyConfig.Jca(key),
                            chain)
                    .build();
            ApkSigner.Builder builder = new ApkSigner.Builder(List.of(config))
                    .setInputApk(request.inputApk.toFile())
                    .setOutputApk(request.outputApk.toFile())
                    .setMinSdkVersion(request.minSdk)
                    .setV1SigningEnabled(request.schemes.contains("v1"))
                    .setV2SigningEnabled(request.schemes.contains("v2"))
                    .setV3SigningEnabled(request.schemes.contains("v3"));
            builder.build().sign();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ouvre le fichier de clés par TYPE EXPLICITE, jamais par sonde.
     * Chaque type absent de la machine ne fait qu'une panne notée ;
     * la PREMIÈRE se garde. L'erreur finale, honnête, liste les types
     * essayés avec leurs pannes ET le remède connu du terrain : un
     * magasin écrit par un keytool récent (PBES2) ne se déchiffre pas
     * sur l'appareil, il se regénère en PKCS12 compatible SHA1/3DES
     * — la propriété {@code keystore.pkcs12.legacy} de la tâche de
     * génération de l'asset du lot C11 l'écrit tel quel.
     *
     * @param fichier    le fichier de clés
     * @param motDePasse le mot de passe du magasin
     * @return le magasin chargé
     * @throws IOException si aucun type n'a chargé le fichier
     */
    static KeyStore ouvrirMagasin(Path fichier, char[] motDePasse)
            throws IOException {
        if (!Files.isRegularFile(fichier)) {
            throw new IOException("keystore file not found: " + fichier);
        }
        List<String> pannes = new ArrayList<>();
        for (String type : new String[] {"PKCS12", "JKS", "BKS"}) {
            KeyStore ouvert = tentative(type, null, fichier,
                    motDePasse, pannes);
            if (ouvert != null) {
                return ouvert;
            }
        }
        Provider complementaire = FOURNISSEUR_COMPLEMENTAIRE;
        if (complementaire != null) {
            for (String type : new String[] {"PKCS12", "BKS"}) {
                KeyStore ouvert = tentative(type, complementaire,
                        fichier, motDePasse, pannes);
                if (ouvert != null) {
                    return ouvert;
                }
            }
        }
        throw new IOException("keystore unreadable: tried PKCS12, JKS, BKS"
                + (complementaire != null
                        ? " then the reflexive provider's PKCS12, BKS"
                        : "")
                + "; " + String.join("; ", pannes)
                + ". Regenerate it as a legacy PKCS12 (SHA1/3DES): a"
                + " keystore written by a modern keytool (PBES2) cannot"
                + " be decrypted on-device.");
    }

    /**
     * Une tentative : le type demandé dans le fournisseur (ou le
     * lecteur installé), chargé à plat ; la panne se note et la
     * tentative suivante part.
     */
    private static KeyStore tentative(String type, Provider fournisseur,
            Path fichier, char[] motDePasse, List<String> pannes) {
        try {
            KeyStore magasin = (fournisseur == null)
                    ? KeyStore.getInstance(type)
                    : KeyStore.getInstance(type, fournisseur);
            try (InputStream entree = Files.newInputStream(fichier)) {
                magasin.load(entree, motDePasse);
            }
            return magasin;
        } catch (Throwable echoue) {
            pannes.add(type + ": " + message(echoue));
            return null;
        }
    }

    /**
     * La clé privée de l'alias : le mot de passe de clé d'abord, puis
     * celui du magasin — le PKCS12 protège la clé par le mot de passe
     * du magasin (un mot de passe de clé distinct n'y est pas
     * interopérable, keytool l'ignore), l'un peut donc déverrouiller
     * ce que l'autre ne fait pas. Un mot de passe trompeur ne fait
     * jamais une panne silencieuse : l'erreur dit l'alias.
     */
    private static PrivateKey cle(KeyStore magasin, String alias,
            char[] motDePasseCle, char[] motDePasseMagasin)
            throws IOException {
        try {
            if (!magasin.containsAlias(alias)) {
                throw new IOException("alias not found in keystore: "
                        + alias);
            }
        } catch (java.security.KeyStoreException echoue) {
            throw new IOException("keystore unreadable: "
                    + echoue.getMessage(), echoue);
        }
        for (char[] candidat : motsDePasse(motDePasseCle,
                motDePasseMagasin)) {
            try {
                PrivateKey cles = (PrivateKey) magasin.getKey(alias,
                        candidat);
                if (cles != null) {
                    return cles;
                }
            } catch (Exception echoue) {
                // ce candidat ne déverrouille pas : l'essai suivant
            }
        }
        throw new IOException("key not recoverable for alias: " + alias
                + " (wrong key password)");
    }

    /** Les mots de passe qui méritent un essai, sans doublon de
     *  contenu — le déverrouillage PKCS12 coûte des milliers de tours
     *  de dérivation sur l'appareil : jamais deux fois le même. */
    private static List<char[]> motsDePasse(char[] motDePasseCle,
            char[] motDePasseMagasin) {
        List<char[]> candidats = new ArrayList<>();
        if (motDePasseCle != null) {
            candidats.add(motDePasseCle);
        }
        if (motDePasseMagasin != null
                && !egal(motDePasseCle, motDePasseMagasin)) {
            candidats.add(motDePasseMagasin);
        }
        return candidats;
    }

    private static boolean egal(char[] premier, char[] second) {
        return premier != null && second != null
                && Arrays.equals(premier, second);
    }

    /**
     * La chaîne de certificats de l'alias — l'entrée du magasin peut
     * porter une chaîne complète, la signature la veut entière ; à
     * défaut, le certificat seul.
     */
    private static List<X509Certificate> chaine(KeyStore magasin,
            String alias) {
        List<X509Certificate> x509 = new ArrayList<>();
        try {
            Certificate[] chain = magasin.getCertificateChain(alias);
            if (chain != null) {
                for (Certificate certificat : chain) {
                    if (certificat instanceof X509Certificate) {
                        x509.add((X509Certificate) certificat);
                    }
                }
            }
            if (x509.isEmpty()) {
                Certificate seul = magasin.getCertificate(alias);
                if (seul instanceof X509Certificate) {
                    x509.add((X509Certificate) seul);
                }
            }
        } catch (Exception echoue) {
            // un magasin lu ne rend pas ici : chaîne vide, la
            // signature dira « certificate not found »
        }
        return x509;
    }

    private static String message(Throwable echoue) {
        String texte = echoue.getMessage();
        return texte != null && !texte.isBlank() ? texte
                : echoue.getClass().getSimpleName();
    }

    @Override
    public Verification verify(Path apk, int minSdk) throws IOException {
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(
                    apk.toFile())
                    .setMinCheckedPlatformVersion(Math.max(1, minSdk))
                    .build()
                    .verify();
            List<String> errors = new ArrayList<>();
            for (ApkVerifier.IssueWithParams issue : result.getErrors()) {
                errors.add(issue.toString());
            }
            return new Verification(result.isVerified(),
                    result.isVerifiedUsingV1Scheme(),
                    result.isVerifiedUsingV2Scheme(),
                    result.isVerifiedUsingV3Scheme(),
                    errors);
        } catch (Exception e) {
            throw new IOException("verification failed: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public String toolVersion() {
        return version;
    }
}
