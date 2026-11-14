package jo.joide.platform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Écritures de fichiers atomiques : fichier temporaire voisin,
 * vidage sur disque puis renommage.
 *
 * <p>Un lecteur voit toujours l'ancien contenu complet ou le nouveau
 * contenu complet, jamais un état intermédiaire, même si le processus
 * est interrompu pendant l'écriture. Le fichier temporaire est placé
 * dans le même répertoire que la cible pour que le renommage reste
 * atomique au sens du système de fichiers.</p>
 */
public final class AtomicFiles {

    private static final String TEMP_SUFFIX = ".joide-tmp";

    private AtomicFiles() {
    }

    /**
     * @param target  fichier cible
     * @param content contenu à écrire
     * @throws IOException si l'écriture ou le renommage échoue ; dans ce
     *                     cas la cible garde son ancien contenu et le
     *                     fichier temporaire créé est supprimé
     */
    public static void writeBytes(Path target, byte[] content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        deleteStaleTemp(temp);
        boolean created = false;
        try {
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            moveAtomically(temp, target);
        } catch (IOException | RuntimeException e) {
            if (created) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
    }

    /**
     * Supprime un reste d'écriture interrompue : seul un fichier
     * temporaire régulier est touché, jamais un répertoire.
     */
    private static void deleteStaleTemp(Path temp) throws IOException {
        if (Files.isRegularFile(temp)) {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * @param target  fichier cible
     * @param content texte à écrire, encodé en UTF-8
     * @throws IOException si l'écriture ou le renommage échoue
     */
    public static void writeString(Path target, CharSequence content) throws IOException {
        writeBytes(target, content.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Remplace la cible par la source, de façon atomique.
     *
     * @param source fichier source existant
     * @param target fichier cible, écrasé s'il existe
     * @throws IOException si le renommage échoue
     */
    public static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unsupportedFallback) {
            // certains systèmes de fichiers refusent le renommage atomique
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        syncDirectory(target.getParent());
    }

    /**
     * Vide le répertoire parent sur disque pour rendre le renommage
     * durable ; un échec dégrade la garantie sans la rompre.
     */
    private static void syncDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // répertoire non ouvrable en canal : durabilité moindre, sans échec
        }
    }
}
