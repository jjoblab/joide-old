package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * La variante d'aapt2 (lot XIX, le terrain du 2026-11-17 : le binaire
 * LINUX de Google Maven partait sur un téléphone ARM64 — l'ELF
 * x86_64 était inexecutable et bionic le filait au shell :
 * « syntax error: unexpected '(' »). Le jeu d'instructions décide :
 * ARM64 sur l'appareil (ReVanced épinglé), x86_64 Android pour les
 * émulateurs, linux au bureau.
 */
class ToolchainLocatorVariantTest {

    @Test
    void laVarianteSuitLEnsembleEtLArchitecture() {
        // bureau : quel que soit le processeur, le jar linux
        assertEquals("linux", ToolchainLocator.varianteAapt2(
                "x86_64", false));
        assertEquals("linux", ToolchainLocator.varianteAapt2(
                "aarch64", false));
        // appareil ARM64 : le pied téléphonique d'aujourd'hui
        assertEquals("android-arm64-v8a", ToolchainLocator.varianteAapt2(
                "aarch64", true));
        // appareil x86_64 (émulateur) : le binaire Android x86_64
        assertEquals("android-x86_64", ToolchainLocator.varianteAapt2(
                "x86_64", true));
        assertEquals("android-x86_64", ToolchainLocator.varianteAapt2(
                "amd64", true));
        // ABI Android inconnue : aucun binaire Android publié pour
        // elle — le repli historique reste le jar linux
        assertEquals("linux", ToolchainLocator.varianteAapt2(
                "mips64", true));
        assertEquals("linux", ToolchainLocator.varianteAapt2(
                null, true));
    }
}
