package jo.joide.app.android.parametres;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android.content.Context;

import jo.joide.platform.AtomicFiles;

/**
 * La clé de chiffrement des clés d'API, dans Android Keystore (lot P,
 * D-6, ADR-058) : alias dédié JoIDE, AES-256, {@code PURPOSE_ENCRYPT
 * | PURPOSE_DECRYPT}, GCM — le composant Android MINCE : il fabrique
 * (ou retrouve) la clé Keystore et l'injecte dans {@link ClesChiffrees}
 * ; tout le format vit dans la classe pure, testée JVM.
 */
public final class KeystoreCles {

    /** L'alias Keystore de la clé de chiffrement. */
    static final String ALIAS = "joide.cles";

    /** Le dossier des fichiers chiffrés. */
    static final String DOSSIER = "cles";

    private KeystoreCles() {
    }

    /**
     * Le stockage chiffré sur le Keystore de l'appareil.
     *
     * @param contexte le contexte de l'application
     * @return le stockage (clé Keystore + fichiers chiffrés)
     */
    public static ClesChiffrees sur(Context contexte) {
        return new ClesChiffrees(cleKeystore(contexte),
                dossier(contexte));
    }

    /** Le dossier des fichiers chiffrés (filesDir/cles). */
    static Path dossier(Context contexte) {
        File fichiers = contexte.getFilesDir();
        return Paths.get(fichiers.getAbsolutePath(), DOSSIER);
    }

    /**
     * La clé Keystore : trouvée ou générée. La génération échouée
     * (Keystore cassé, appareil exotique) retombe sur une clé en
     * mémoire — les clés posées deviennent illisibles mais
     * l'application démarre : jamais un crash au boot pour une clé
     * d'API.
     */
    static SecretKey cleKeystore(Context contexte) {
        try {
            KeyStore magasin = KeyStore.getInstance("AndroidKeyStore");
            magasin.load(null);
            KeyStore.Entry entree = magasin.getEntry(ALIAS, null);
            if (entree instanceof KeyStore.SecretKeyEntry cle) {
                return cle.getSecretKey();
            }
            KeyGenerator generateur = KeyGenerator.getInstance("AES",
                    "AndroidKeyStore");
            generateur.init(new android.security.keystore
                    .KeyGenParameterSpec.Builder(ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                            | android.security.keystore.KeyProperties
                            .PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties
                            .BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                            android.security.keystore.KeyProperties
                                    .ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generateur.generateKey();
        } catch (Exception e) {
            // le repli SÉMENTÉ (le 2026-11-18 : l'ancien repli
            // mémoire perdait les clés au redémarrage — « la clé
            // n'est pas vraiment stockée », le rapport de l'écran
            // Paramètres) : une amorce de 32 octets persistée dans
            // filesDir/cles/.seed dérive la clé AES. Le coffre
            // matériel manque, la clé reste privée au bac à sable de
            // l'application et survit au redémarrage comme aux
            // processus — les clés posées restent lisibles.
            try {
                return cleDeSemance(contexte);
            } catch (Exception impossible) {
                throw new IllegalStateException(
                        "no AES available: " + impossible.getMessage(),
                        impossible);
            }
        }
    }

    /** La clé dérivée de l'amorce persistée (le repli du repli :
     *  AES-GCM sur une SecretKeySpec des 32 octets du fichier). */
    private static SecretKey cleDeSemance(Context contexte)
            throws IOException {
        Path fichier = dossier(contexte).resolve(".seed");
        byte[] amorce;
        if (Files.isRegularFile(fichier)) {
            amorce = Files.readAllBytes(fichier);
        } else {
            amorce = new byte[32];
            new SecureRandom().nextBytes(amorce);
            Files.createDirectories(fichier.getParent());
            AtomicFiles.writeBytes(fichier, amorce);
        }
        if (amorce.length != 32) {
            throw new IOException("seed corrompue");
        }
        return new SecretKeySpec(amorce, "AES");
    }
}
