package jo.joide.app.android.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le moteur de rendu Markdown du transcript IA (terrain du
 * 2026-11-22 : « les réponses ne sont pas bien représentées — plus
 * modernes, plus propres ») — le geste de l'IDE de référence : un
 * analyseur PUR (aucune dépendance Android, testable JVM) qui coupe
 * un texte en BLOCS, la vue traduit les blocs en vues ; l'inline
 * (gras, italique, code, liens) part en MARQUAGES sur le texte
 * nettoyé, la vue y pose les spans.
 *
 * <p>Blocs reconnus : titres {@code #} à {@code ######}, paragraphes,
 * blocs de code clôturés ({@code ```} avec langue), listes à puces et
 * ordonnées (imbrication par indentation), citations
 * {@code >} (récursives), séparateurs {@code ---}. Toute syntaxe
 * inconnue dégrade en paragraphe — jamais d'échec sur du texte
 * libre.</p>
 */
public final class RenduMarkdown {

    /** Les genres de bloc. */
    public enum GenreBloc {
        /** Un titre, niveau 1 à 6. */
        TITRE,
        /** Un paragraphe de texte libre. */
        PARAGRAPHE,
        /** Un bloc de code clôturé (langue éventuelle). */
        CODE,
        /** Une liste (puces ou ordonnée, imbrication). */
        LISTE,
        /** Une citation (blocs récursifs). */
        CITATION,
        /** Un séparateur thématique. */
        SEPARATEUR
    }

    /** Une marque inline sur le texte nettoyé. */
    public enum Marque {
        /** Le fragment est en gras. */
        GRAS,
        /** Le fragment est en italique. */
        ITALIQUE,
        /** Le fragment est du code en ligne. */
        CODE,
        /** Le fragment est un lien (la cible suit). */
        LIEN
    }

    /** Un marquage : la marque, sa portée [debut, fin[ du texte
     *  nettoyé, et sa cible (l'URL d'un lien). */
    public record Marquage(Marque marque, int debut, int fin,
                           String cible) {
    }

    /** Une ligne nettoyée : le texte sans marqueurs, et ses
     *  marquages. */
    public record Ligne(String texte, List<Marquage> marquages) {
    }

    /** Un bloc du document. */
    public static final class Bloc {
        /** Le genre du bloc. */
        public final GenreBloc genre;
        /** TITRE : le niveau (1..6). */
        public final int niveau;
        /** TITRE/PARAGRAPHE/CODE : le texte (avec marqueurs inline
         *  pour TITRE/PARAGRAPHE, brut pour CODE). */
        public final String texte;
        /** CODE : la langue énoncée après la clôture. */
        public final String langue;
        /** LISTE : les éléments. */
        public final List<ElementListe> elements;
        /** CITATION : les blocs internes. */
        public final List<Bloc> interne;

        Bloc(GenreBloc genre, int niveau, String texte, String langue,
             List<ElementListe> elements, List<Bloc> interne) {
            this.genre = genre;
            this.niveau = niveau;
            this.texte = texte;
            this.langue = langue;
            this.elements = elements;
            this.interne = interne;
        }
    }

    /** Un élément de liste : niveau d'imbrication, ordonné ou puce,
     *  son numéro (les listes ordonnées), le texte. */
    public record ElementListe(int niveau, boolean ordonne, int numero,
                                String texte) {
    }

    private static final Pattern TITRE = Pattern.compile(
            "^(#{1,6})\\s+(.*)$");
    private static final Pattern PUCE = Pattern.compile(
            "^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern NUMERO = Pattern.compile(
            "^(\\s*)(\\d+)[.)]\\s+(.*)$");
    private static final Pattern SEPARATEUR = Pattern.compile(
            "^(?:-{3,}|\\*{3,}|_{3,})\\s*$");
    private static final Pattern CLOTURE = Pattern.compile(
            "^```(.*)$");
    private static final Pattern CITATION = Pattern.compile(
            "^>\\s?(.*)$");

    private RenduMarkdown() {
    }

    /**
     * Analyse un texte en blocs (pure : jamais d'échec, tout dégrade
     * en paragraphe).
     *
     * @param source le texte markdown (ou libre)
     * @return les blocs dans l'ordre
     */
    public static List<Bloc> analyser(String source) {
        List<Bloc> blocs = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return blocs;
        }
        String[] lignes = source.split("\n", -1);
        int i = 0;
        StringBuilder paragraphe = new StringBuilder();
        while (i < lignes.length) {
            String brut = lignes[i];
            String ligne = brut.stripTrailing();
            Matcher cloture = CLOTURE.matcher(ligne);
            if (cloture.matches()) {
                viderParagraphe(blocs, paragraphe);
                String langue = cloture.group(1).strip();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lignes.length
                        && !CLOTURE.matcher(lignes[i].stripTrailing())
                        .matches()) {
                    code.append(lignes[i]).append('\n');
                    i++;
                }
                // une clôture manquante : le code va jusqu'à la fin
                // (jamais d'échec sur du texte libre)
                if (i < lignes.length) {
                    i++;
                }
                blocs.add(new Bloc(GenreBloc.CODE, 0,
                        code.toString(), langue, null, null));
                continue;
            }
            if (ligne.isBlank()) {
                viderParagraphe(blocs, paragraphe);
                i++;
                continue;
            }
            Matcher separateur = SEPARATEUR.matcher(ligne);
            if (separateur.matches()) {
                viderParagraphe(blocs, paragraphe);
                blocs.add(new Bloc(GenreBloc.SEPARATEUR, 0, "", "",
                        null, null));
                i++;
                continue;
            }
            Matcher titre = TITRE.matcher(ligne);
            if (titre.matches()) {
                viderParagraphe(blocs, paragraphe);
                blocs.add(new Bloc(GenreBloc.TITRE,
                        titre.group(1).length(), titre.group(2),
                        "", null, null));
                i++;
                continue;
            }
            if (CITATION.matcher(ligne).matches()) {
                viderParagraphe(blocs, paragraphe);
                List<String> internes = new ArrayList<>();
                while (i < lignes.length
                        && CITATION.matcher(
                        lignes[i].stripTrailing()).matches()) {
                    Matcher citation = CITATION.matcher(
                            lignes[i].stripTrailing());
                    if (citation.matches()) {
                        internes.add(citation.group(1));
                    }
                    i++;
                }
                blocs.add(new Bloc(GenreBloc.CITATION, 0, "", "",
                        null, analyser(String.join("\n", internes))));
                continue;
            }
            if (PUCE.matcher(ligne).matches()
                    || NUMERO.matcher(ligne).matches()) {
                viderParagraphe(blocs, paragraphe);
                List<ElementListe> elements = new ArrayList<>();
                while (i < lignes.length) {
                    String courante = lignes[i].stripTrailing();
                    Matcher puce = PUCE.matcher(courante);
                    Matcher numero = NUMERO.matcher(courante);
                    if (puce.matches()) {
                        elements.add(new ElementListe(
                                niveau(puce.group(1)), false, 0,
                                puce.group(2)));
                    } else if (numero.matches()) {
                        elements.add(new ElementListe(
                                niveau(numero.group(1)), true,
                                Integer.parseInt(numero.group(2)),
                                numero.group(3)));
                    } else {
                        break;
                    }
                    i++;
                }
                blocs.add(new Bloc(GenreBloc.LISTE, 0, "", "", elements,
                        null));
                continue;
            }
            paragraphe.append(paragraphe.length() > 0 ? "\n" : "")
                    .append(ligne);
            i++;
        }
        viderParagraphe(blocs, paragraphe);
        return blocs;
    }

    private static void viderParagraphe(List<Bloc> blocs,
                                         StringBuilder paragraphe) {
        if (paragraphe.length() > 0) {
            blocs.add(new Bloc(GenreBloc.PARAGRAPHE, 0,
                    paragraphe.toString(), "", null, null));
            paragraphe.setLength(0);
        }
    }

    /** Le niveau d'imbrication d'un élément : deux espaces par
     *  niveau, trois au plus. */
    private static int niveau(String indentation) {
        return Math.min(3, indentation.length() / 2);
    }

    /**
     * Le texte d'une ligne, NETTOYÉ de ses marqueurs, avec ses
     * marquages (gras, italique, code en ligne, liens). Les marqueurs
     * non appariés restent du texte — jamais un mot perdu.
     *
     * @param source la ligne avec marqueurs
     * @return la ligne nettoyée et ses marquages
     */
    public static Ligne enLigne(String source) {
        String s = source == null ? "" : source;
        StringBuilder net = new StringBuilder();
        List<Marquage> marquages = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '`') {
                int fin = s.indexOf('`', i + 1);
                if (fin > i) {
                    marquages.add(new Marquage(Marque.CODE,
                            net.length(), net.length() + fin - i - 1,
                            null));
                    net.append(s, i + 1, fin);
                    i = fin + 1;
                    continue;
                }
            }
            if (c == '*' && i + 1 < s.length() && s.charAt(i + 1)
                    == '*') {
                int fin = s.indexOf("**", i + 2);
                if (fin > i) {
                    marquages.add(new Marquage(Marque.GRAS,
                            net.length(), net.length() + fin - i - 2,
                            null));
                    net.append(s, i + 2, fin);
                    i = fin + 2;
                    continue;
                }
            }
            if (c == '_' && i + 1 < s.length() && s.charAt(i + 1)
                    == '_') {
                int fin = s.indexOf("__", i + 2);
                if (fin > i) {
                    marquages.add(new Marquage(Marque.GRAS,
                            net.length(), net.length() + fin - i - 2,
                            null));
                    net.append(s, i + 2, fin);
                    i = fin + 2;
                    continue;
                }
            }
            if ((c == '*' || c == '_') && i + 1 < s.length()
                    && !Character.isWhitespace(s.charAt(i + 1))) {
                char cherche = c;
                int fin = -1;
                for (int j = i + 1; j < s.length(); j++) {
                    if (s.charAt(j) == cherche
                            && !Character.isWhitespace(s.charAt(j - 1))) {
                        fin = j;
                        break;
                    }
                }
                if (fin > i) {
                    marquages.add(new Marquage(Marque.ITALIQUE,
                            net.length(), net.length() + fin - i - 1,
                            null));
                    net.append(s, i + 1, fin);
                    i = fin + 1;
                    continue;
                }
            }
            if (c == '[') {
                int crochet = s.indexOf("](", i + 1);
                if (crochet > i) {
                    int parenthese = s.indexOf(')', crochet + 2);
                    if (parenthese > crochet) {
                        String texte = s.substring(i + 1, crochet);
                        String cible = s.substring(crochet + 2,
                                parenthese);
                        marquages.add(new Marquage(Marque.LIEN,
                                net.length(),
                                net.length() + texte.length(), cible));
                        net.append(texte);
                        i = parenthese + 1;
                        continue;
                    }
                }
            }
            net.append(c);
            i++;
        }
        return new Ligne(net.toString(), marquages);
    }
}
