package jo.joide.deps.resolve;

import java.io.IOException;

/**
 * Petit utilitaire de fixture : écrit un artefact complet (POM sans
 * dépendances + jar avec checksums) pour une coordonnée et version.
 */
final class Artifacts {

    void write(RepoFixture fixture, String ga, String version)
            throws IOException {
        String g = ga.split(":")[0];
        String a = ga.split(":")[1];
        fixture.pom(g, a, version, "<project><groupId>" + g + "</groupId>"
                + "<artifactId>" + a + "</artifactId><version>" + version
                + "</version></project>");
        fixture.artifact(g, a, version, "jar",
                RepoFixture.jarBytes(ga + ":" + version));
    }
}
