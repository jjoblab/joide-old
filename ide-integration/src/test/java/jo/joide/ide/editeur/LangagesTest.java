package jo.joide.ide.editeur;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Le registre des langages s'assemble sur la JVM (paquets intégrés de
 * la brique, analyseurs écrits) : Java, Kotlin et XML se résolvent
 * depuis le nom de fichier — les langages sans analyseur écrit
 * retombent honnêtement.
 */
final class LangagesTest {

    @Test
    void javaKotlinXmlSeResolventDuNomDeFichier() {
        var java = Langages.de("Main.java");
        assertNotNull(java, "Java se résout");
        assertEquals("java", java.id());

        var kotlin = Langages.de("Depot.kt");
        assertNotNull(kotlin, "Kotlin se résout");
        assertEquals("kotlin", kotlin.id());

        var script = Langages.de("build.gradle.kts");
        assertNotNull(script, "KTS se résout");

        var xml = Langages.de("activity_main.xml");
        assertNotNull(xml, "XML se résout");
        assertEquals("xml", xml.id());
    }

    @Test
    void leRegistreNEstAssembleQuUneFois() {
        var premier = Langages.registre();
        var second = Langages.registre();
        assertSame(premier, second, "le registre est un singleton paresseux");
    }

    @Test
    void langageInconnuRendNullPasUneException() {
        assertNull(Langages.de("donnees.bin"));
    }
}
