package jo.joide.app.android.parametres;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import jo.joide.platform.AtomicFiles;

/**
 * Le stockage chiffré des clés d'API (lot P, décision D-6, ADR-058) :
 * un fichier par fournisseur, AES-GCM avec IV aléatoire par
 * écriture, écriture atomique — la clé de chiffrement vit dans
 * Android Keystore ({@link KeystoreCles}), jamais dans le dépôt, JAMAIS
 * de clés de secours en clair.
 *
 * <p>Le format {@code joide.cles/1} est une ligne d'en-tête lisible
 * (le fichier se reconnaît, l'audit s'en sert) suivie du chiffré
 * brut IV (12 octets) + texte. Le cœur est PUR : testé JVM avec une
 * clé forgée ; seule la fabrication de la clé est un composant
 * Android.</p>
 */
public final class ClesChiffrees {

    /** L'en-tête du format. */
    static final String ENTETE = "joide.cles/1";

    /** La taille de l'IV GCM (octets). */
    private static final int TAILLE_IV = 12;

    /** Le tag GCM (bits). */
    private static final int TAG_BITS = 128;

    private final SecretKey cle;
    private final Path dossier;
    private final SecureRandom alea;

    /**
     * @param cle     la clé de chiffrement (Keystore en production)
     * @param dossier le dossier des fichiers (filesDir/cles)
     */
    public ClesChiffrees(SecretKey cle, Path dossier) {
        this(cle, dossier, new SecureRandom());
    }

    /**
     * @param cle     la clé de chiffrement
     * @param dossier le dossier des fichiers
     * @param alea    la source d'aléa (les tests l'injectent)
     */
    ClesChiffrees(SecretKey cle, Path dossier, SecureRandom alea) {
        this.cle = cle;
        this.dossier = dossier;
        this.alea = alea;
    }

    /**
     * Pose (ou remplace) la clé d'un fournisseur.
     *
     * @param fournisseur l'identifiant du fournisseur
     * @param cleApi      la clé d'API (en clair à l'entrée seulement)
     * @throws IOException l'écriture a échoué
     */
    public void poser(String fournisseur, String cleApi)
            throws IOException {
        byte[] iv = new byte[TAILLE_IV];
        alea.nextBytes(iv);
        byte[] chiffre = chiffrer(iv,
                cleApi.getBytes(StandardCharsets.UTF_8));
        byte[] fichier = new byte[ENTETE.length() + 1
                + TAILLE_IV + chiffre.length];
        System.arraycopy(ENTETE.getBytes(StandardCharsets.US_ASCII),
                0, fichier, 0, ENTETE.length());
        fichier[ENTETE.length()] = '\n';
        System.arraycopy(iv, 0, fichier, ENTETE.length() + 1,
                TAILLE_IV);
        System.arraycopy(chiffre, 0,
                fichier, ENTETE.length() + 1 + TAILLE_IV,
                chiffre.length);
        Files.createDirectories(dossier);
        AtomicFiles.writeBytes(dossier.resolve(nomFichier(fournisseur)),
                fichier);
    }

    /**
     * @param fournisseur l'identifiant du fournisseur
     * @return la clé d'API, ou null (absente ou illisible : jamais
     *         une exception qui bloquerait l'envoi)
     * @throws IOException la lecture a échoué
     */
    public String lire(String fournisseur) throws IOException {
        Path cible = dossier.resolve(nomFichier(fournisseur));
        if (!Files.isRegularFile(cible)) {
            return null;
        }
        byte[] contenu = Files.readAllBytes(cible);
        int debut = ENTETE.length() + 1;
        if (contenu.length <= debut
                || !ENTETE.equals(new String(contenu, 0,
                        ENTETE.length(), StandardCharsets.US_ASCII))) {
            return null;
        }
        byte[] iv = new byte[TAILLE_IV];
        System.arraycopy(contenu, debut, iv, 0, TAILLE_IV);
        byte[] chiffre = new byte[contenu.length - debut - TAILLE_IV];
        System.arraycopy(contenu, debut + TAILLE_IV, chiffre, 0,
                chiffre.length);
        try {
            return new String(dechiffrer(iv, chiffre),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // un fichier d'une autre clé (réinstallation) : absent
            // honnête, jamais un crash au démarrage
            return null;
        }
    }

    /**
     * @param fournisseur l'identifiant du fournisseur
     * @return vrai si une clé est posée
     */
    public boolean estPosee(String fournisseur) {
        try {
            return lire(fournisseur) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Efface la clé d'un fournisseur.
     *
     * @param fournisseur l'identifiant du fournisseur
     * @throws IOException la suppression a échoué
     */
    public void effacer(String fournisseur) throws IOException {
        Files.deleteIfExists(
                dossier.resolve(nomFichier(fournisseur)));
    }

    private byte[] chiffrer(byte[] iv, byte[] clair) {
        try {
            Cipher chiffre = Cipher.getInstance("AES/GCM/NoPadding");
            chiffre.init(Cipher.ENCRYPT_MODE, cle,
                    new GCMParameterSpec(TAG_BITS, iv));
            return chiffre.doFinal(clair);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "AES-GCM unavailable: " + e.getMessage());
        }
    }

    private byte[] dechiffrer(byte[] iv, byte[] chiffre)
            throws GeneralSecurityException {
        Cipher dechiffre;
        dechiffre = Cipher.getInstance("AES/GCM/NoPadding");
        dechiffre.init(Cipher.DECRYPT_MODE, cle,
                new GCMParameterSpec(TAG_BITS, iv));
        return dechiffre.doFinal(chiffre);
    }

    private static String nomFichier(String fournisseur) {
        // le fournisseur est un identifiant interne (anthropic,
        // openai), le nom de fichier n'expose rien
        return fournisseur.replaceAll("[^a-z0-9-]", "-") + ".cle";
    }
}
