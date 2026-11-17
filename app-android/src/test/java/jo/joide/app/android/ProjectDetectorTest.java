package jo.joide.app.android;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDetectorTest {

    @Test
    void nativeWorkspaceWinsOverGradleScripts() {
        ProjectDetector.Detection d = ProjectDetector.detect(List.of(
                "joide.toml", "build.gradle", "src", ".git"));
        assertEquals(ProjectDetector.Kind.NATIVE, d.kind());
        assertTrue(d.compatBanner().isEmpty());
    }

    @Test
    void gradleWorkspaceNeedsCompatBanner() {
        ProjectDetector.Detection d = ProjectDetector.detect(List.of(
                "settings.gradle.kts", "build.gradle.kts", "app", ".gitignore"));
        assertEquals(ProjectDetector.Kind.GRADLE, d.kind());
        assertTrue(d.compatBanner().isPresent());
        assertTrue(d.compatBanner().get().contains("compatibilité"));
    }

    @Test
    void unknownRootIsSaidUnknown() {
        assertEquals(ProjectDetector.Kind.UNKNOWN,
                ProjectDetector.detect(List.of("README.md", "photos")).kind());
    }

    @Test
    void caseAndNullsNeverBreakDetection() {
        assertEquals(ProjectDetector.Kind.NATIVE,
                ProjectDetector.detect(List.of("JOIDE.TOML")).kind());
        assertEquals(ProjectDetector.Kind.UNKNOWN,
                ProjectDetector.detect(java.util.Arrays.asList(null, "")).kind());
    }
}
