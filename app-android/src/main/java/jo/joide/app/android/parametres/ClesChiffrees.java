package jo.joide.app.android.parametres;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

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
 *
 * <p>Lot C3 (2026-11-19) : l'IV aléatoire vient DU FOURNISSEUR — le
 * Cipher le génère à l'{@code init} de CHIFFREMENT (sans
 * {@code GCMParameterSpec}), on le relit par {@code getIV()} et on
 * l'écrit dans le fichier. La clé Keystore EXIGE la randomisation :
 * un IV posé par l'appelant est REFUSÉ en chiffrement
 * (« Caller-provided IV not permitted » — le plantage du bouton
 * Sauver du 2026-11-19 10:23) ; le déchiffrement, lui, le REQUIERT
 * (il relit l'IV du fichier) — asymétrie de l'AOSP, respectée ici.</p>
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

    /**
     * @param cle     la clé de chiffrement (Keystore en production)
     * @param dossier le dossier des fichiers (filesDir/cles)
     */
    public ClesChiffrees(SecretKey cle, Path dossier) {
        this.cle = cle;
        this.dossier = dossier;
    }

    /**
     * Pose (ou remplace) la clé d'un fournisseur.
     *
     * <p>Lot C3 : l'IV ne se FABRIQUE plus côté appelant — le
     * fournisseur le génère (le Keystore l'EXIGE : randomisation
     * armée par défaut, un IV fourni est refusé en chiffrement), le
     * {@code init} sans {@code GCMParameterSpec} le produit et
     * {@code getIV()} le relit aussitôt : il s'écrit dans le fichier
     * comme avant, le FORMAT ne change pas. La randomisation reste
     * effective : deux poses du même texte diffèrent (le test
     * l'éprouve).</p>
     *
     * @param fournisseur l'identifiant du fournisseur
     * @param cleApi      la clé d'API (en clair à l'entrée seulement)
     * @throws IOException l'écriture a échoué
     */
    public void poser(String fournisseur, String cleApi)
            throws IOException {
        byte[] iv;
        byte[] chiffre;
        try {
            Cipher chiffreur = Cipher.getInstance("AES/GCM/NoPadding");
            chiffreur.init(Cipher.ENCRYPT_MODE, cle);
            iv = chiffreur.getIV();
            if (iv == null || iv.length != TAILLE_IV) {
                throw new GeneralSecurityException("IV aleatoire absent ("
                        + (iv == null ? "null" : iv.length + " octets")
                        + ", " + TAILLE_IV + " attendus)");
            }
            chiffre = chiffreur.doFinal(
                    cleApi.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "AES-GCM unavailable: " + e.getMessage(), e);
        }
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

    /** Le déchiffrement relit l'IV du fichier : l'appelant le
     *  fournit — PERMIS en mode DÉCHIFFREMENT (l'asymétrie AOSP :
     *  exigée au relire, refusée au chiffrer). */
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
