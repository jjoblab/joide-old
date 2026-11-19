package jo.joide.app.android.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.ui.RenduMarkdown.Bloc;
import jo.joide.app.android.ui.RenduMarkdown.GenreBloc;
import jo.joide.app.android.ui.RenduMarkdown.Ligne;
import jo.joide.app.android.ui.RenduMarkdown.Marque;

/**
 * Le moteur de rendu Markdown du transcript IA (terrain du
 * 2026-11-22 : « plus moderne, plus intelligent, rendu propre ») —
 * l'analyseur PUR éprouvé JVM : blocs, titres, listes, citations,
 * séparateurs, code clôturé ; l'inline nettoyé avec ses marquages,
 * jamais un mot perdu sur un marqueur non apparié.
 */
class RenduMarkdownTest {

    @Test
    void lesBlocsSeDecoupentDansLOrdre() {
        List<Bloc> blocs = RenduMarkdown.analyser(
                "# Titre\n\ntexte libre\n\n```java\nint x = 1;\n```\n"
                        + "\n- un\n- deux\n\ncitation :\n\n> dit\n"
                        + "\n---\n");
        assertEquals(GenreBloc.TITRE, blocs.get(0).genre);
        assertEquals(1, blocs.get(0).niveau);
        assertEquals("Titre", blocs.get(0).texte);
        assertEquals(GenreBloc.PARAGRAPHE, blocs.get(1).genre);
        assertEquals("texte libre", blocs.get(1).texte);
        assertEquals(GenreBloc.CODE, blocs.get(2).genre);
        assertEquals("java", blocs.get(2).langue);
        assertTrue(blocs.get(2).texte.contains("int x = 1;"));
        assertEquals(GenreBloc.LISTE, blocs.get(3).genre);
        assertEquals(2, blocs.get(3).elements.size());
        assertEquals("un", blocs.get(3).elements.get(0).texte());
        assertEquals(GenreBloc.PARAGRAPHE, blocs.get(4).genre);
        assertEquals("citation :", blocs.get(4).texte);
        assertEquals(GenreBloc.CITATION, blocs.get(5).genre);
        assertEquals(1, blocs.get(5).interne.size());
        assertEquals(GenreBloc.SEPARATEUR, blocs.get(6).genre);
    }

    @Test
    void lesListesPortentImbricationEtOrdre() {
        List<Bloc> blocs = RenduMarkdown.analyser(
                "1. premier\n2. deuxième\n"
                        + "  - imbriqué\n    - profond\n3. troisième");
        List<RenduMarkdown.ElementListe> elements = blocs.get(0).elements;
        assertTrue(elements.get(0).ordonne());
        assertEquals(1, elements.get(0).numero());
        assertEquals(1, elements.get(2).niveau(),
                "deux espaces = un niveau");
        assertEquals(2, elements.get(3).niveau(),
                "quatre espaces = deux niveaux");
        assertTrue(elements.get(3).numero() == 0);
        assertTrue(elements.get(4).ordonne());
        assertEquals(3, elements.get(4).numero());
    }

    @Test
    void uneClotureManquanteVaJusquaLaFinSansEchouer() {
        List<Bloc> blocs = RenduMarkdown.analyser(
                "texte\n```\ndu code\nsans clôture");
        assertEquals(2, blocs.size());
        assertEquals(GenreBloc.PARAGRAPHE, blocs.get(0).genre);
        assertEquals(GenreBloc.CODE, blocs.get(1).genre);
        assertTrue(blocs.get(1).texte.contains("du code"));
    }

    @Test
    void lInlineSeNettoieSansPerdreUnMot() {
        Ligne ligne = RenduMarkdown.enLigne(
                "voici **du gras**, *de l'italique*, `du code` "
                        + "et [un lien](https://exemple.fr)");
        assertEquals("voici du gras, de l'italique, du code et un lien",
                ligne.texte());
        assertEquals(4, ligne.marquages().size());
        assertEquals(Marque.GRAS, ligne.marquages().get(0).marque());
        assertEquals(6, ligne.marquages().get(0).debut());
        assertEquals(13, ligne.marquages().get(0).fin());
        assertEquals(Marque.ITALIQUE, ligne.marquages().get(1).marque());
        assertEquals(Marque.CODE, ligne.marquages().get(2).marque());
        assertEquals(Marque.LIEN, ligne.marquages().get(3).marque());
        assertEquals("https://exemple.fr",
                ligne.marquages().get(3).cible());
        assertEquals(41, ligne.marquages().get(3).debut());
        assertEquals(48, ligne.marquages().get(3).fin());
    }

    @Test
    void lesMarqueursNonAppariesRestentDuTexte() {
        // jamais un mot perdu : un astérisque solitaire reste écrit
        Ligne ligne = RenduMarkdown.enLigne("2 * 3 et _ seul");
        assertEquals("2 * 3 et _ seul", ligne.texte());
        assertTrue(ligne.marquages().isEmpty()
                        || ligne.marquages().stream().noneMatch(
                        m -> m.marque() == Marque.GRAS),
                "aucune paire inventée");
    }

    @Test
    void leTexteLibreResteUnParagrapheTelQuel() {
        List<Bloc> blocs = RenduMarkdown.analyser("une phrase simple");
        assertEquals(1, blocs.size());
        assertEquals(GenreBloc.PARAGRAPHE, blocs.get(0).genre);
        Ligne ligne = RenduMarkdown.enLigne("une phrase simple");
        assertEquals("une phrase simple", ligne.texte());
        assertTrue(ligne.marquages().isEmpty(),
                "aucun marquage sur du texte libre");
    }
}
