package jo.joide.deps.resolve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import jo.joide.deps.Checksums;
import jo.joide.deps.repo.FakeTransport;
import jo.joide.deps.repo.Layout;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.repo.TransportResponse;

/**
 * Fixture de dépôt Maven : construit sur disque la disposition
 * Maven 2 d'artefacts de test (POM, GMM, jars, AAR, métadonnées,
 * checksums) et l'expose comme dépôt local ou distant factice.
 */
final class RepoFixture {

    static final String BASE_URL = "https://repo.example/maven2";

    final Path dir;
    final FakeTransport transport;

    private RepoFixture(Path dir, FakeTransport transport) {
        this.dir = dir;
        this.transport = transport;
    }

    static RepoFixture create(Path base) throws IOException {
        Files.createDirectories(base);
        return new RepoFixture(base, new FakeTransport(base, BASE_URL));
    }

    /** @param id identifiant @return le dépôt local équivalent */
    Repository.Local toLocal(String id) {
        return new Repository.Local(id, dir);
    }

    /** @param id identifiant @return le dépôt distant factice */
    Repository.Remote toRemote(String id) {
        return new Repository.Remote(id, BASE_URL, transport);
    }

    void write(String relPath, byte[] content) throws IOException {
        Path file = dir.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    void writeText(String relPath, String text) throws IOException {
        write(relPath, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Écrit le POM d'une version. */
    void pom(String g, String a, String v, String xml) throws IOException {
        writeText(Layout.pomPath(g, a, v), xml);
    }

    /** Écrit le GMM (.module) d'une version. */
    void gmm(String g, String a, String v, String json) throws IOException {
        writeText(Layout.modulePath(g, a, v), json);
    }

    /**
     * Écrit l'artefact principal d'une version (jar ou aar selon
     * l'extension) avec son checksum .sha256 et .sha1 concordants.
     */
    void artifact(String g, String a, String v, String ext, byte[] content)
            throws IOException {
        String relPath = Layout.artifactPath(g, a, v, null, ext);
        write(relPath, content);
        writeText(relPath + ".sha256", Checksums.sha256Hex(content) + "\n");
        writeText(relPath + ".sha1", Checksums.sha1Hex(content) + "\n");
    }

    /** Comme {@link #artifact} mais avec un .sha256 délibérément faux. */
    void artifactWithBadSha256(String g, String a, String v, String ext,
                               byte[] content) throws IOException {
        String relPath = Layout.artifactPath(g, a, v, null, ext);
        write(relPath, content);
        String wrong = new StringBuilder(Checksums.sha256Hex(content))
                .reverse().toString();
        writeText(relPath + ".sha256", wrong + "\n");
    }

    /** Comme {@link #artifact} mais sans aucun checksum publié. */
    void artifactWithoutChecksums(String g, String a, String v, String ext,
                                  byte[] content) throws IOException {
        write(Layout.artifactPath(g, a, v, null, ext), content);
    }

    /** Métadonnées d'artefact (liste des versions). */
    void artifactMetadata(String g, String a, String xml) throws IOException {
        writeText(Layout.artifactMetadataPath(g, a), xml);
    }

    /** Métadonnées de version (état d'instantané). */
    void versionMetadata(String g, String a, String v, String xml)
            throws IOException {
        writeText(Layout.versionMetadataPath(g, a, v), xml);
    }

    /** Fichier servi à la place (corruption ciblée du transport). */
    void override(String relPath, byte[] content) {
        transport.override(BASE_URL + "/" + relPath, content);
    }

    static byte[] jarBytes(String marker) {
        // Un contenu déterministe reconnaissable, pas un vrai jar :
        // le résolveur ne l'ouvre jamais, il le hache.
        return ("fake-jar:" + marker).getBytes(StandardCharsets.UTF_8);
    }
}
