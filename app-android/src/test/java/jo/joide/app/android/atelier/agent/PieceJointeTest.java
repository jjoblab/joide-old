package jo.joide.app.android.atelier.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.agent.PartieContenu;
import jo.joide.app.android.atelier.agent.PieceJointe.Genre;

/**
 * Les pièces jointes du composeur éprouvées JVM (lot S, ADR-064) :
 * la fusion met les blocs balisés DEVANT la question, les images
 * deviennent des parties multimodales dans l'ordre, les noms
 * suivent, le texte borné s'annonce, l'arbre se borne en lignes.
 */
class PieceJointeTest {

    @Test
    void laFusionPoseLesBlocsDevantLaQuestion() {
        List<PieceJointe> pieces = List.of(
                PieceJointe.fichier("MainActivity.java", "int x = 1;"),
                PieceJointe.projet("src/Main.java\nbuild.gradle.kts"));
        String fondu = PieceJointe.fondre(
                "pourquoi ce build échoue ?", pieces);
        assertTrue(fondu.startsWith(
                "<file path=\"MainActivity.java\">\nint x = 1;\n"
                        + "</file>\n\n"));
        assertTrue(fondu.contains("<project_tree>\nsrc/Main.java\n"
                + "build.gradle.kts\n</project_tree>\n\n"));
        assertTrue(fondu.endsWith("pourquoi ce build échoue ?"));
    }

    @Test
    void sansPiecesLeMessagePasseTelQuel() {
        assertEquals("la question",
                PieceJointe.fondre("la question", List.of()));
        assertEquals("la question", PieceJointe.fondre("la question",
                null));
    }

    @Test
    void lesImagesDeviennentDesPartiesDansLOrdre() {
        List<PieceJointe> pieces = List.of(
                PieceJointe.image("capture.png", "image/png", "AAAA"),
                PieceJointe.fichier("note.md", "du texte"),
                PieceJointe.image("photo.jpg", "image/jpeg", "BBBB"));
        List<PartieContenu> images = PieceJointe.images(pieces);
        assertEquals(2, images.size());
        assertEquals(PartieContenu.Genre.IMAGE, images.get(0).genre);
        assertEquals("image/png", images.get(0).mediaType);
        assertEquals("AAAA", images.get(0).donnees);
        assertEquals("BBBB", images.get(1).donnees);
        // les noms suivent TOUTES les pièces, dans l'ordre
        assertEquals(List.of("capture.png", "note.md", "photo.jpg"),
                PieceJointe.noms(pieces));
    }

    @Test
    void uneImageVideNePartPas() {
        List<PieceJointe> pieces = List.of(
                PieceJointe.image("vide.png", "image/png", ""));
        assertTrue(PieceJointe.images(pieces).isEmpty());
    }

    @Test
    void leTexteBorneSannonce() {
        StringBuilder longTexte = new StringBuilder();
        for (int i = 0; i < 1_000; i++) {
            longTexte.append("dix caractères");
        }
        String borne = PieceJointe.bornerTexte(longTexte.toString());
        assertTrue(borne.length() > PieceJointe.TEXTE_MAX);
        assertTrue(borne.startsWith("dix caractères"));
        assertTrue(borne.contains("read_file a le reste"));
        assertEquals(PieceJointe.TEXTE_MAX,
                borne.indexOf("\n[… tronqué"));
    }

    @Test
    void leTexteCourtPasseEntier() {
        assertEquals("court", PieceJointe.bornerTexte("court"));
    }

    @Test
    void larbreSeBorneEnLignesAnnoncees() {
        List<String> chemins = new java.util.ArrayList<>();
        for (int i = 0; i < PieceJointe.ARBRE_MAX_LIGNES + 12; i++) {
            chemins.add("src/Fichier" + i + ".java");
        }
        String arbre = PieceJointe.arbre(chemins);
        assertTrue(arbre.contains("src/Fichier399.java"));
        assertTrue(arbre.endsWith("[… 12 fichiers de plus]"));
        assertTrue(!arbre.contains("src/Fichier400.java\n"));
        assertEquals("", PieceJointe.arbre(List.of()));
        assertEquals("", PieceJointe.arbre(null));
    }

    @Test
    void lesGenresPortentLeurNom() {
        assertEquals(Genre.IMAGE,
                PieceJointe.image("i", "image/png", "AA").genre);
        assertEquals(Genre.FICHIER,
                PieceJointe.fichier("f", "t").genre);
        assertEquals(Genre.PROJET, PieceJointe.projet("t").genre);
    }
}
