package jo.joide.build.jvm.graph;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Scan d'en-tête d'un fichier source Java ou Kotlin : paquet et
 * imports (simples et à la demande).
 *
 * <p>Volontairement léger et tolérant : le scanner ne lève jamais
 * d'exception, ignore commentaires, chaînes et textes bruts, et rend
 * « paquet par défaut » quand aucune déclaration n'est lisible. Les
 * imports à la demande (« a.b.* ») sont la clé de l'invalidation par
 * changement d'espace de noms : ils sont conservés séparément.</p>
 */
public final class SourceHeaderScanner {

    /** En-tête extrait d'un fichier source. */
    public static final class Header {
        /** Paquet en forme pointée, vide si paquet par défaut. */
        public final String packageName;
        /** Imports simples, en forme pointée, sans doublons. */
        public final List<String> singleImports;
        /** Paquets des imports à la demande, en forme pointée. */
        public final List<String> starImports;

        /**
         * @param packageName  paquet en forme pointée
         * @param singleImports imports simples
         * @param starImports   paquets importés à la demande
         */
        public Header(String packageName, List<String> singleImports,
               List<String> starImports) {
            this.packageName = packageName;
            this.singleImports = singleImports;
            this.starImports = starImports;
        }
    }

    private SourceHeaderScanner() {
        // utilitaire statique
    }

    /**
     * @param file     fichier source, jamais {@code null} (l'extension
     *                 détermine le style de fin de déclaration)
     * @param content  octets du fichier
     * @return l'en-tête extrait, jamais {@code null}
     */
    public static Header scan(Path file, byte[] content) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(content, "content");
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean kotlin = name.endsWith(".kt");
        String text = new String(content, StandardCharsets.UTF_8);
        Parser parser = new Parser(text, kotlin);
        return parser.scan();
    }

    /** Machine à états minimale : code, ligne, bloc, chaîne, texte brut. */
    private static final class Parser {
        private final String text;
        private final boolean kotlin;
        private final StringBuilder word = new StringBuilder();
        private String packageName = "";
        private final List<String> singles = new ArrayList<>();
        private final List<String> stars = new ArrayList<>();

        Parser(String text, boolean kotlin) {
            this.text = text;
            this.kotlin = kotlin;
        }

        Header scan() {
            int i = 0;
            int n = text.length();
            String state = "code"; // code, line, block, string, char, raw
            while (i < n) {
                char c = text.charAt(i);
                char next = i + 1 < n ? text.charAt(i + 1) : '\0';
                switch (state) {
                    case "code":
                        if (c == '/' && next == '/') {
                            state = "line";
                            i++;
                        } else if (c == '/' && next == '*') {
                            state = "block";
                            i++;
                        } else if (c == '"') {
                            if (kotlin && next == '"') {
                                state = "raw";
                                i++;
                            } else {
                                state = "string";
                            }
                        } else if (c == '\'') {
                            state = "char";
                        } else if (Character.isJavaIdentifierPart(c)
                                || c == '.' || c == '*') {
                            word.append(c);
                        } else {
                            flushWord();
                        }
                        break;
                    case "line":
                        if (c == '\n') {
                            state = "code";
                        }
                        break;
                    case "block":
                        if (c == '*' && next == '/') {
                            state = "code";
                            i++;
                        }
                        break;
                    case "string":
                        if (c == '\\') {
                            i++;
                        } else if (c == '"') {
                            state = "code";
                        }
                        break;
                    case "char":
                        if (c == '\\') {
                            i++;
                        } else if (c == '\'') {
                            state = "code";
                        }
                        break;
                    case "raw":
                        // séquence fermante """ non suivie d'une
                        // quatrième quote ; "" seul reste du contenu
                        if (c == '"' && next == '"'
                                && (i + 2 >= n || text.charAt(i + 2) != '"')) {
                            state = "code";
                            i += 2;
                        } else if (c == '"' && next == '"') {
                            i++;
                        }
                        break;
                    default:
                        state = "code";
                        break;
                }
                i++;
            }
            flushWord();
            return new Header(packageName, singles, stars);
        }

        /** Termine le mot courant et repère package / import. */
        private void flushWord() {
            if (word.length() == 0) {
                return;
            }
            String token = word.toString();
            word.setLength(0);
            if (token.equals("package")) {
                expecting = "package";
            } else if (token.equals("import")) {
                expecting = "import";
            } else if ("package".equals(expecting)) {
                packageName = token;
                expecting = null;
            } else if ("import".equals(expecting)) {
                if (token.endsWith(".*")) {
                    String starPackage = token.substring(0,
                            token.length() - 2);
                    if (!stars.contains(starPackage)) {
                        stars.add(starPackage);
                    }
                } else if (!token.endsWith(".") && !singles.contains(token)) {
                    singles.add(token);
                }
                // Kotlin : un import par ligne, Java : « ; » — le mot
                // suivant qui n'est pas un identifiant termine de toute
                // façon l'attente au flush suivant
            }
        }

        private String expecting;
    }
}
