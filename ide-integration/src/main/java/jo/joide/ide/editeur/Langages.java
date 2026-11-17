package jo.joide.ide.editeur;

import java.util.Set;
import jo.celeris.core.intelligence.WordCompletion;
import jo.celeris.core.language.Completion;
import jo.celeris.core.language.LanguagePlatform;
import jo.celeris.core.language.LanguageRegistry;
import jo.celeris.core.language.model.LanguageSupport;

/**
 * Le registre des langages de l'éditeur, assemblé UNE fois : les
 * vingt-quatre paquets intégrés de la brique (celeris core-language)
 * — analyseurs ÉCRITS pour Java, Kotlin, KTS, Groovy, XML et JSON,
 * grammaires TextMate compilées pour les autres (fabrique
 * core-syntax-grammars) — et la complétion générique par les mots du
 * document en repli.
 *
 * <p>L'assemblage se fait à la première demande ; les grammaires se
 * compilent dans le chargeur de paquets de la brique (hors fil UI à
 * l'initiative de l'hôte — ADR-007 celeris).</p>
 */
final class Langages {

    private static volatile LanguageRegistry registre;

    private Langages() {
    }

    /** Le registre assemblé (paresseux, une fois). */
    static LanguageRegistry registre() {
        LanguageRegistry present = registre;
        if (present != null) {
            return present;
        }
        synchronized (Langages.class) {
            present = registre;
            if (present == null) {
                present = LanguagePlatform.with(
                                jo.celeris.core.syntax.grammars.PackGrammars.INSTANCE)
                        .builtinPacks()
                        .generic(Completion.class,
                                WordCompletion.INSTANCE)
                        .build();
                registre = present;
            }
            return present;
        }
    }

    /**
     * Le support de langage d'un nom de fichier, ou null (texte plat).
     *
     * @param nomFichier le nom du fichier (« Main.java »)
     * @return le support résolu, ou null
     */
    static LanguageSupport de(String nomFichier) {
        var detection = registre().detect(nomFichier, null, null);
        if (detection instanceof jo.celeris.core.language.LanguageDetection.Resolved resolu) {
            return resolu.support();
        }
        return null;
    }

    /** Mots réservés Java (repli du coach, langages servis par JoIDE). */
    static final Set<String> MOTS_JAVA = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default",
            "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "var",
            "record", "sealed", "permits", "yield");

    /** Mots réservés Kotlin. */
    static final Set<String> MOTS_KOTLIN = Set.of(
            "as", "break", "by", "class", "continue", "do", "else",
            "false", "for", "fun", "if", "in", "interface", "is",
            "null", "object", "package", "return", "super", "this",
            "throw", "true", "try", "typealias", "val", "var", "when",
            "while");

    /** Mots notables XML (balises et attributs courants). */
    static final Set<String> MOTS_XML = Set.of(
            "layout", "TextView", "Button", "EditText", "ImageView",
            "LinearLayout", "ConstraintLayout", "FrameLayout",
            "android", "app", "tools", "include", "merge", "View");
}
