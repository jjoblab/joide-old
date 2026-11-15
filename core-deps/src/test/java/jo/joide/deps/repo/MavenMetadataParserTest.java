package jo.joide.deps.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * maven-metadata.xml : versions, latest/release, instantanés.
 */
class MavenMetadataParserTest {

    @Test
    void parsesArtifactLevelMetadata() {
        String xml = "<?xml version=\"1.0\"?>"
                + "<metadata>"
                + "  <groupId>org.example</groupId>"
                + "  <artifactId>lib</artifactId>"
                + "  <versioning>"
                + "    <latest>2.0-SNAPSHOT</latest>"
                + "    <release>1.9</release>"
                + "    <versions>"
                + "      <version>1.7</version>"
                + "      <version>1.8</version>"
                + "      <version>1.9</version>"
                + "      <version>2.0-SNAPSHOT</version>"
                + "    </versions>"
                + "  </versioning>"
                + "</metadata>";
        MavenMetadataParser.Result r = MavenMetadataParser.parse(xml);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        assertEquals(4, r.metadata.versions.size());
        assertEquals("1.9", r.metadata.release);
        assertEquals("2.0-SNAPSHOT", r.metadata.latest);
        assertEquals(3, r.metadata.nonSnapshotVersions().size());
        assertNull(r.metadata.snapshot);
    }

    @Test
    void parsesVersionLevelSnapshotMetadata() {
        String xml = "<metadata>"
                + "  <groupId>org.example</groupId>"
                + "  <artifactId>lib</artifactId>"
                + "  <version>2.0-SNAPSHOT</version>"
                + "  <versioning>"
                + "    <snapshot>"
                + "      <timestamp>20260115.142030</timestamp>"
                + "      <buildNumber>7</buildNumber>"
                + "    </snapshot>"
                + "    <snapshotVersions>"
                + "      <snapshotVersion>"
                + "        <extension>jar</extension>"
                + "        <value>2.0-20260115.142030-7</value>"
                + "        <updated>20260115142030</updated>"
                + "      </snapshotVersion>"
                + "      <snapshotVersion>"
                + "        <extension>pom</extension>"
                + "        <value>2.0-20260115.142030-7</value>"
                + "      </snapshotVersion>"
                + "    </snapshotVersions>"
                + "  </versioning>"
                + "</metadata>";
        MavenMetadataParser.Result r = MavenMetadataParser.parse(xml);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        assertEquals("20260115.142030", r.metadata.snapshot.timestamp);
        assertEquals("7", r.metadata.snapshot.buildNumber);
        assertEquals(2, r.metadata.snapshot.snapshotVersions.size());
        assertEquals("2.0-20260115.142030-7",
                r.metadata.snapshot.valueFor("jar", null));
        assertEquals("2.0-20260115.142030-7",
                r.metadata.snapshot.valueFor("pom", null));
        assertNull(r.metadata.snapshot.valueFor("aar", null));
        assertNull(r.metadata.snapshot.valueFor("jar", "sources"));
    }

    @Test
    void brokenDocumentsAreReported() {
        MavenMetadataParser.Result broken = MavenMetadataParser.parse(
                "<metadata><versioning>");
        assertTrue(!broken.problems.isEmpty());
        MavenMetadataParser.Result wrongRoot = MavenMetadataParser.parse(
                "<other/>");
        assertNull(wrongRoot.metadata);
        assertTrue(!wrongRoot.problems.isEmpty());
    }
}
