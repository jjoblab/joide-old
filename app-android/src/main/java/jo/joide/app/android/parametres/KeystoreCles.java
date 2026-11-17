package jo.joide.app.android.parametres;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import android.content.Context;

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
        return new ClesChiffrees(cleKeystore(),
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
    static SecretKey cleKeystore() {
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
            // repli mémoire : la session vit, les fichiers d'avant
            // ne se relisent pas (l'erreur de lecture est honnête)
            try {
                KeyGenerator enMemoire =
                        KeyGenerator.getInstance("AES");
                enMemoire.init(256);
                return enMemoire.generateKey();
            } catch (Exception impossible) {
                throw new IllegalStateException(
                        "no AES available: "
                                + impossible.getMessage());
            }
        }
    }
}
