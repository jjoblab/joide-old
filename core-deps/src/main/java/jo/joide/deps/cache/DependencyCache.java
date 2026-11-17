package jo.joide.deps.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import jo.joide.deps.Checksums;
import jo.joide.platform.AtomicFiles;

/**
 * Cache disque partagé entre projets, adressé par contenu (ADR-030) :
 * un artefact ou un fichier de métadonnées y vit sous son empreinte
 * SHA-256 — deux projets qui résolvent le même artefact partagent le
 * même fichier, immuable par construction, et la vérification est la
 * clé elle-même.
 *
 * <p>Structure : {@code files/xx/<sha256>} pour les contenus,
 * {@code aars/xx/<sha256>/} pour les AAR extraits,
 * {@code tmp/} pour la zone d'attente. Les écritures passent par la
 * zone d'attente puis un déplacement atomique : deux processus
 * concurrents qui écrivent le même contenu tombent d'accord sur le
 * même fichier final ; le perdant du déplacement abandonne simplement
 * sa copie.</p>
 */
public final class DependencyCache {

    private final Path root;

    public DependencyCache(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve("files"));
        Files.createDirectories(root.resolve("aars"));
        Files.createDirectories(root.resolve("tmp"));
    }

    /** @return la racine du cache */
    public Path root() {
        return root;
    }

    /** @param sha256 empreinte en hexadécimal minuscule
     *  @return le chemin du contenu, présent ou non */
    public Path fileOf(String sha256) {
        return root.resolve("files").resolve(sha256.substring(0, 2))
                .resolve(sha256);
    }

    /** @param sha256 empreinte
     *  @return vrai si le contenu est déjà au cache */
    public boolean has(String sha256) {
        if (sha256 == null || sha256.length() < 3) {
            return false;
        }
        return Files.isRegularFile(fileOf(sha256));
    }

    /**
     * Lit un contenu du cache.
     *
     * @param sha256 empreinte attendue
     * @return les octets, ou {@code null} si absent
     * @throws IOException si la lecture échoue
     */
    public byte[] read(String sha256) throws IOException {
        if (!has(sha256)) {
            return null;
        }
        byte[] content = Files.readAllBytes(fileOf(sha256));
        // Le contenu stocké sous cette clé doit hacher vers cette
        // clé ; une divergence signifie une corruption ou une écriture
        // interrompue — le fichier est retiré et lu comme absent.
        if (!Checksums.matches(sha256, Checksums.sha256Hex(content))) {
            try {
                Files.delete(fileOf(sha256));
            } catch (IOException e) {
                // deletion concurrente : sans importance
            }
            return null;
        }
        return content;
    }

    /**
     * Écrit un contenu au cache et retourne son empreinte. Idempotent
     * et concurrent-sûr : si le contenu y est déjà, rien n'est écrit.
     *
     * @param content contenu à stocker
     * @return l'empreinte SHA-256 hexadécimale minuscule
     * @throws IOException si l'écriture échoue
     */
    public String write(byte[] content) throws IOException {
        String sha256 = Checksums.sha256Hex(content);
        if (has(sha256)) {
            return sha256;
        }
        Path target = fileOf(sha256);
        Files.createDirectories(target.getParent());
        Path staging = root.resolve("tmp").resolve(
                "write-" + sha256 + "-" + System.nanoTime());
        AtomicFiles.writeBytes(staging, content);
        try {
            AtomicFiles.moveAtomically(staging, target);
        } catch (IOException e) {
            // Un concurrent a gagné le déplacement : le but est atteint.
            if (has(sha256)) {
                try {
                    Files.delete(staging);
                } catch (IOException e2) {
                    // la zone tmp se vide au nettoyage
                }
                return sha256;
            }
            throw e;
        }
        return sha256;
    }

    /**
     * @param sha256 empreinte de l'AAR
     * @return le répertoire d'extraction de cet AAR (prêt à écrire)
     */
    public Path aarDir(String sha256) {
        return root.resolve("aars").resolve(sha256.substring(0, 2))
                .resolve(sha256);
    }

    /** @param sha256 empreinte de l'AAR
     *  @return vrai si l'AAR est déjà extrait */
    public boolean hasAar(String sha256) {
        if (sha256 == null || sha256.length() < 3) {
            return false;
        }
        return Files.isDirectory(aarDir(sha256));
    }

    /**
     * Nettoie la zone d'attente (appel de fin de résolution).
     */
    public void clearStaging() {
        Path tmp = root.resolve("tmp");
        try (Stream<Path> entries = Files.list(tmp)) {
            for (Path p : entries.sorted(Comparator.reverseOrder())
                    .toList()) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // occupé par un concurrent : laisser
                }
            }
        } catch (IOException e) {
            // tmp absent : rien à nettoyer
        }
    }

    /**
     * @return la taille occupée par les contenus, en octets
     */
    public long sizeBytes() {
        long total = 0;
        Path files = root.resolve("files");
        try (Stream<Path> walk = Files.walk(files)) {
            Optional<Long> sum = walk.filter(Files::isRegularFile)
                    .map(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reduce(Long::sum);
            total = sum.orElse(0L);
        } catch (IOException e) {
            return 0;
        }
        return total;
    }
}
