package jo.joide.app.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import android.content.Context;

/**
 * Préparation de la chaîne de build sur appareil : le keystore de
 * débogage embarqué en asset (généré à la construction par la tâche
 * Gradle {@code genererKeystoreDebug} — jamais commis, convention
 * docs/dev.md) se copie UNE fois vers le répertoire d'outillage.
 *
 * <p>Sans lui, la variante debug des projets utilisateurs restait NON
 * SIGNÉE : la tâche de signature ne s'enregistrait que sur un
 * keystore posé ({@code AndroidTaskFactory}, lot U) — l'APK produit
 * n'était pas installable directement sur l'appareil.</p>
 */
public final class PreparationChaine {

    /** L'asset du keystore de débogage (PKCS12, alias « joide »). */
    static final String ASSET_KEYSTORE = "toolchain/debug.keystore";

    private PreparationChaine() {
    }

    /**
     * Assure la présence du keystore de débogage dans le répertoire
     * d'outillage ({@code filesDir/toolchain}) : copie idempotente de
     * l'asset, écriture atomique. Appeler sur un fil de fond (entrées
     * /sorties).
     *
     * @param contexte le contexte de l'application
     * @return le chemin du keystore, ou {@code null} si l'asset est
     *         absent (APK antérieur à la tâche) — la variante restera
     *         non signée, écart honnête jamais un crash
     */
    public static Path assurerKeystoreDebug(Context contexte) {
        Path cible = Paths.get(contexte.getFilesDir().getAbsolutePath(),
                "toolchain", "debug.keystore");
        try {
            if (Files.isRegularFile(cible) && Files.size(cible) > 0) {
                return cible;
            }
            Files.createDirectories(cible.getParent());
            Path temporaire = cible.resolveSibling(
                    cible.getFileName() + ".tmp-"
                            + System.nanoTime());
            try (InputStream entree = contexte.getAssets()
                    .open(ASSET_KEYSTORE);
                 OutputStream sortie = Files.newOutputStream(temporaire)) {
                entree.transferTo(sortie);
            }
            if (Files.size(temporaire) == 0) {
                Files.deleteIfExists(temporaire);
                return null;
            }
            try {
                Files.move(temporaire, cible,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException e) {
                Files.move(temporaire, cible,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return cible;
        } catch (IOException e) {
            // l'asset absent (build ancien) ou le disque plein : le
            // build continue NON signé, l'écart se dira à la console
            return null;
        }
    }
}
