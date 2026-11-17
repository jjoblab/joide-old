package jo.joide.app.android.parametres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Les mesures de stockage du lot M (ADR-É8) : chaque valeur vient du
 * disque — répertoire absent vaut 0, arborescence sommée, vidage qui
 * garde le répertoire, formatage en mégaoctets.
 */
class MesuresStockageTest {

    @TempDir
    Path bac;

    @Test
    void unRepertoireAbsentVautZero() throws IOException {
        assertEquals(0L, MesuresStockage.octets(bac.resolve("inconnu")));
    }

    @Test
    void unRepertoireVideVautZero() throws IOException {
        Path vide = bac.resolve("cidx");
        Files.createDirectories(vide);
        assertEquals(0L, MesuresStockage.octets(vide));
    }

    @Test
    void larborescenceEstSommeeFichiersSeulement() throws IOException {
        Path m2 = bac.resolve("m2");
        Path groupe = m2.resolve("com/exemple/outil/1.0");
        Files.createDirectories(groupe);
        Path profonde = m2.resolve("a/b/c/d");
        Files.createDirectories(profonde);
        Files.write(groupe.resolve("outil-1.0.jar"), new byte[2048]);
        Files.write(groupe.resolve("outil-1.0.pom"), new byte[512]);
        Files.write(profonde.resolve("note.txt"), new byte[1]);
        assertEquals(2048 + 512 + 1, MesuresStockage.octets(m2));
    }

    @Test
    void leVidageGardeLeRepertoireEtRetireLeContenu() throws IOException {
        Path cidx = bac.resolve("cidx");
        Path dedans = cidx.resolve("sous/dossier");
        Files.createDirectories(dedans);
        Files.write(cidx.resolve("index.bin"), new byte[10]);
        Files.write(dedans.resolve("autre.bin"), new byte[10]);
        MesuresStockage.vider(cidx);
        assertTrue(Files.isDirectory(cidx),
                "le répertoire reste : les moteurs le recréent");
        try (var fluide = Files.list(cidx)) {
            List<Path> restants = fluide.collect(java.util.stream
                    .Collectors.toList());
            assertTrue(restants.isEmpty());
        }
        // vider un répertoire absent ne fait rien, jamais d'exception
        MesuresStockage.vider(bac.resolve("inconnu"));
    }

    @Test
    void leFormatMoSuitLaMaquette() {
        assertEquals("0.0", MesuresStockage.formaterMo(0, Locale.ROOT));
        assertEquals("1.0",
                MesuresStockage.formaterMo(1024 * 1024, Locale.ROOT));
        assertEquals("42.0",
                MesuresStockage.formaterMo(42L * 1024 * 1024,
                        Locale.ROOT));
        // l'arrondi d'affichage ne cache jamais la vraie valeur
        assertEquals("42.3", MesuresStockage.formaterMo(
                42L * 1024 * 1024 + 300_000, Locale.ROOT));
        assertEquals("240", MesuresStockage.formaterMo(
                240L * 1024 * 1024, Locale.ROOT));
        // une décimale sous 100 Mo, entier au-delà — jamais de
        // mesure inventée, l'arrondi n'est qu'affichage
        assertEquals("99.9", MesuresStockage.formaterMo(
                (long) (99.94 * 1024 * 1024), Locale.ROOT));
        assertEquals("100", MesuresStockage.formaterMo(
                100L * 1024 * 1024, Locale.ROOT));
    }

    @Test
    void leVidageNettoieLesProfondeursDansNimporteQuelOrdre()
            throws IOException {
        Path racine = bac.resolve("projets");
        Path projet = racine.resolve("mon-app/src/main/java/jo");
        Files.createDirectories(projet);
        Files.write(projet.resolve("Main.java"), new byte[3]);
        Files.write(racine.resolve("joide.toml"), new byte[2]);
        MesuresStockage.vider(racine);
        assertEquals(0L, MesuresStockage.octets(racine));
    }

    @Test
    void lesFichiersSontTriesPourUnVidageDeterministe() throws IOException {
        // le vidage parcourt en ordre inverse : les enfants partent
        // avant les parents — ce test épingle le contrat
        Path racine = bac.resolve("x");
        Files.createDirectories(racine.resolve("a/b"));
        Files.write(racine.resolve("a/b/c.txt"), new byte[1]);
        Files.write(racine.resolve("a/d.txt"), new byte[1]);
        MesuresStockage.vider(racine);
        try (var fluide = Files.walk(racine)) {
            assertEquals(1, fluide.count(), "seule la racine reste");
        }
    }
}
