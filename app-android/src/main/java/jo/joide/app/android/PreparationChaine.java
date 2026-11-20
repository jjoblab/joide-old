package jo.joide.app.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;

import jo.joide.platform.AtomicFiles;

/**
 * Préparation de la chaîne de build sur appareil : le keystore de
 * débogage embarqué en asset (généré à la construction par la tâche
 * Gradle {@code genererKeystoreDebug} — jamais commis, convention
 * docs/dev.md) se pose vers le répertoire d'outillage.
 *
 * <p>Sans lui, la variante debug des projets utilisateurs restait NON
 * SIGNÉE : la tâche de signature ne s'enregistrait que sur un
 * keystore posé ({@code AndroidTaskFactory}, lot U) — l'APK produit
 * n'était pas installable directement sur l'appareil.</p>
 *
 * <p>Lot C11 : la copie SUIVRA toujours l'asset embarqué. Un APK
 * ancien y laissait un keystore PKCS12 moderne (PBES2) que le
 * lecteur de l'appareil ne déchiffre pas — la signature rendait
 * « Unrecognized keystore format » et l'ancienne copie idempotente
 * (« présent et non vide : ne rien refaire ») gardait le fichier
 * mauvais pour toujours. L'empreinte SHA-256 de l'asset se tient à
 * côté du fichier ({@code debug.keystore.marque}) : une empreinte
 * changée — un APK mis à jour — recopie l'asset ; une marque absente
 * ou muette recopie aussi ; la copie et la marque se réécrivent
 * ensemble, atomiques.</p>
 */
public final class PreparationChaine {

    /** L'asset du keystore de débogage (PKCS12, alias « joide »). */
    static final String ASSET_KEYSTORE = "toolchain/debug.keystore";

    private PreparationChaine() {
    }

    /**
     * Assure la présence du keystore de débogage dans le répertoire
     * d'outillage ({@code filesDir/toolchain}) : copie de l'asset
     * quand la marque dit que le fichier ne suit plus (première
     * pose, APK mis à jour, marque perdue), écriture atomique.
     * Appeler sur un fil de fond (entrées/sorties).
     *
     * @param contexte le contexte de l'application
     * @return le chemin du keystore, ou {@code null} si l'asset est
     *         absent (APK antérieur à la tâche) — la variante restera
     *         non signée, écart honnête jamais un crash
     */
    public static Path assurerKeystoreDebug(Context contexte) {
        Path cible = chemin(contexte, "debug.keystore");
        Path marque = chemin(contexte, "debug.keystore.marque");
        byte[] octets;
        try {
            octets = lireAsset(contexte);
        } catch (IOException e) {
            // l'asset absent (build ancien) : le build continue NON
            // signé, l'écart se dira à la console
            return null;
        }
        if (octets.length == 0) {
            return null;
        }
        String empreinte = empreinte(octets);
        try {
            if (Files.isRegularFile(cible) && Files.size(cible) > 0
                    && Files.isRegularFile(marque)
                    && empreinte.contentEquals(Files.readString(marque))) {
                return cible;
            }
            Files.createDirectories(cible.getParent());
            AtomicFiles.writeBytes(cible, octets);
            AtomicFiles.writeString(marque, empreinte);
            return cible;
        } catch (IOException e) {
            // le disque plein ou l'écriture impossible : le build
            // continue NON signé, l'écart se dira à la console
            return null;
        }
    }

    /** Le chemin d'un fichier du répertoire d'outillage. */
    private static Path chemin(Context contexte, String nom) {
        return Paths.get(contexte.getFilesDir().getAbsolutePath(),
                "toolchain", nom);
    }

    /**
     * L'asset lu en entier — à la main : {@code readAllBytes} et
     * {@code transferTo} n'existent qu'à l'API 33, minSdk 28 (même
     * discipline que la copie du lot C1).
     */
    private static byte[] lireAsset(Context contexte) throws IOException {
        try (InputStream entree = contexte.getAssets()
                .open(ASSET_KEYSTORE);
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            byte[] morceau = new byte[65_536];
            int lus;
            while ((lus = entree.read(morceau)) > 0) {
                sortie.write(morceau, 0, lus);
            }
            return sortie.toByteArray();
        }
    }

    /** L'empreinte SHA-256 en hexadécimal — la marque qui décide de
     *  la recopie (SHA-256 : obligatoire sur toute machine qui porte
     *  l'application). */
    private static String empreinte(byte[] octets) {
        MessageDigest condenseur;
        try {
            condenseur = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 absent", impossible);
        }
        byte[] condense = condenseur.digest(octets);
        StringBuilder hexa = new StringBuilder(condense.length * 2);
        for (byte octet : condense) {
            hexa.append(Character.forDigit((octet >> 4) & 0xF, 16));
            hexa.append(Character.forDigit(octet & 0xF, 16));
        }
        return hexa.toString();
    }
}
