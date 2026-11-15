package jo.joide.imports.gradle.script;

import java.util.ArrayList;
import java.util.List;
import jo.joide.platform.Problem;

/**
 * Parseur statique et tolérant des scripts Gradle (Groovy ou Kotlin
 * DSL), par blocs à accolades, conscient des chaînes et des
 * commentaires.
 *
 * <p>Le parseur ne lève jamais d'exception et n'évalue jamais rien :
 * il produit une suite d'instructions en forme tolérante ; ce qui
 * n'est pas compris devient une instruction {@code UNKNOWN} avec son
 * texte et sa ligne, et un problème est enregistré. Le contrat
 * complet est l'ADR-039.</p>
 */
public final class ScriptParser {

    private ScriptParser() {
    }

    /**
     * @param source texte du script (Groovy ou Kotlin DSL)
     * @return le script analysé, jamais d'exception — en cas de bug
     *         interne, un script vide avec un problème d'erreur
     */
    public static Script parse(String source) {
        if (source == null) {
            return new Script(List.of(), List.of());
        }
        try {
            List<Problem> problems = new ArrayList<>();
            List<ScriptToken> tokens =
                    new ScriptLexer(source, problems).tokens();
            return new Parsing(tokens, problems).script();
        } catch (RuntimeException e) {
            return new Script(List.of(), List.of(Problem.error(
                    "script-parser-internal",
                    "internal parser failure: " + e.getClass().getName())));
        }
    }

    /** État du parcours : jetons, position, problèmes. */
    private static final class Parsing {

        private static final List<String> DECLARATIONS =
                List.of("def", "val", "var", "final");
        private static final List<String> WORD_LITERALS =
                List.of("true", "false", "null");

        private final List<ScriptToken> tokens;
        private final List<Problem> problems;
        private int index;

        Parsing(List<ScriptToken> tokens, List<Problem> problems) {
            this.tokens = tokens;
            this.problems = problems;
        }

        /** Analyse jusqu'à la fin ou à l'accolade fermante (non consommée). */
        Script script() {
            List<ScriptStmt> statements = new ArrayList<>();
            while (true) {
                skipSeparators();
                ScriptToken token = current();
                if (token.type() == ScriptToken.Type.EOF
                        || isPunct(token, "}")) {
                    return new Script(statements, problems);
                }
                statements.add(statement());
                resynchronize();
            }
        }

        private ScriptStmt statement() {
            ScriptToken token = current();
            if (token.type() != ScriptToken.Type.IDENT) {
                return unknown();
            }
            int line = token.line();
            List<String> chain = new ArrayList<>();
            chain.add(token.text());
            advance();
            absorbDeclaration(chain);
            while (isPunct(current(), ".")
                    && peek(1).type() == ScriptToken.Type.IDENT) {
                chain.add(peek(1).text());
                advance();
                advance();
            }
            String name = chain.get(chain.size() - 1);
            List<String> receiver =
                    chain.subList(0, chain.size() - 1);
            if (isPunct(current(), "=")) {
                advance();
                return ScriptStmt.assign(join(chain), "=",
                        expression(), line);
            }
            if (isPunct(current(), "+") && isPunct(peek(1), "=")) {
                advance();
                advance();
                return ScriptStmt.assign(join(chain), "+=",
                        expression(), line);
            }
            if (isPunct(current(), "(")) {
                List<ScriptExpr> args = arguments();
                Script block = optionalBlock();
                return ScriptStmt.call(receiver, name, args, block, line);
            }
            if (isPunct(current(), "{")) {
                Script block = block();
                return ScriptStmt.call(receiver, name, List.of(),
                        block, line);
            }
            if (isLiteral(current())) {
                List<ScriptExpr> args = new ArrayList<>();
                args.add(expression());
                while (isPunct(current(), ",")) {
                    advance();
                    args.add(expression());
                }
                // Groovy attache une fermeture finale à l'appel :
                // implementation 'g:a:v' { exclude group: 'x' }
                return ScriptStmt.call(receiver, name, args,
                        optionalBlock(), line);
            }
            if (current().type() == ScriptToken.Type.IDENT
                    && isPunct(peek(1), ":")) {
                List<ScriptExpr> args = new ArrayList<>();
                args.add(namedEntries());
                return ScriptStmt.call(receiver, name, args,
                        optionalBlock(), line);
            }
            if (current().type() == ScriptToken.Type.IDENT
                    && WORD_LITERALS.contains(current().text())) {
                ScriptExpr arg = expression();
                return ScriptStmt.call(receiver, name, List.of(arg),
                        null, line);
            }
            // appel non parenthésé à argument d'appel ou de référence :
            // implementation files('x'), api project(':core'),
            // implementation libs.foo.bar
            if (current().type() == ScriptToken.Type.IDENT
                    && (isPunct(peek(1), "(") || isPunct(peek(1), "."))) {
                ScriptExpr arg = expression();
                return ScriptStmt.call(receiver, name, List.of(arg),
                        optionalBlock(), line);
            }
            return ScriptStmt.call(receiver, name, List.of(), null, line);
        }

        /** Mots-clés de déclaration : « def x », « val x », « var x ». */
        private void absorbDeclaration(List<String> chain) {
            if (chain.size() == 1 && DECLARATIONS.contains(chain.get(0))
                    && current().type() == ScriptToken.Type.IDENT) {
                chain.set(0, current().text());
                advance();
            }
        }

        /** Arguments entre parenthèses, tolérants aux séparateurs absents. */
        private List<ScriptExpr> arguments() {
            List<ScriptExpr> args = new ArrayList<>();
            advance();
            if (isPunct(current(), ")")) {
                advance();
                return args;
            }
            while (current().type() != ScriptToken.Type.EOF) {
                // virgule finale : (a, b,) — licite en Kotlin
                if (isPunct(current(), ",")
                        && isPunct(peek(1), ")")) {
                    advance();
                    break;
                }
                args.add(expression());
                if (isPunct(current(), ",")) {
                    // virgule finale : (a, b,) — licite en Kotlin
                    if (isPunct(peek(1), ")")) {
                        advance();
                        break;
                    }
                    advance();
                    continue;
                }
                if (isPunct(current(), ")")) {
                    break;
                }
                problems.add(Problem.warn("script-args-unexpected",
                        "expected ',' or ')' at line "
                                + current().line()));
                while (current().type() != ScriptToken.Type.EOF
                        && !isPunct(current(), ",")
                        && !isPunct(current(), ")")) {
                    advance();
                }
                if (isPunct(current(), ",")) {
                    advance();
                }
            }
            expect(")");
            return args;
        }

        /** Bloc en fermeture s'il suit, sinon rien. */
        private Script optionalBlock() {
            return isPunct(current(), "{") ? block() : null;
        }

        private Script block() {
            advance();
            Script body = script();
            expect("}");
            return body;
        }

        private ScriptExpr expression() {
            ScriptToken token = current();
            switch (token.type()) {
                case STRING_DOUBLE:
                    advance();
                    return new ScriptExpr.Literal(token.text(),
                            ScriptExpr.Literal.Style.DOUBLE);
                case STRING_SINGLE:
                    advance();
                    return new ScriptExpr.Literal(token.text(),
                            ScriptExpr.Literal.Style.SINGLE);
                case STRING_TRIPLE_DOUBLE:
                    advance();
                    return new ScriptExpr.Literal(token.text(),
                            ScriptExpr.Literal.Style.TRIPLE_DOUBLE);
                case STRING_TRIPLE_SINGLE:
                    advance();
                    return new ScriptExpr.Literal(token.text(),
                            ScriptExpr.Literal.Style.TRIPLE_SINGLE);
                case NUMBER:
                    advance();
                    return new ScriptExpr.Literal(token.text(),
                            ScriptExpr.Literal.Style.NUMBER);
                default:
                    break;
            }
            if (isPunct(token, "-")
                    && peek(1).type() == ScriptToken.Type.NUMBER) {
                advance();
                String raw = "-" + current().text();
                advance();
                return new ScriptExpr.Literal(raw,
                        ScriptExpr.Literal.Style.NUMBER);
            }
            if (token.type() == ScriptToken.Type.IDENT) {
                if (isPunct(peek(1), ":")) {
                    return namedEntries();
                }
                if (WORD_LITERALS.contains(token.text())) {
                    advance();
                    return new ScriptExpr.Literal(token.text(),
                            ScriptExpr.Literal.Style.OTHER);
                }
                return referenceOrCall();
            }
            if (isPunct(token, "[")) {
                return listOrMap();
            }
            if (isPunct(token, "{")) {
                return new ScriptExpr.ClosureExpr(block());
            }
            return new ScriptExpr.OtherExpr(rawTail());
        }

        /** Chaîne d'identifiants, éventuellement terminée par un appel. */
        private ScriptExpr referenceOrCall() {
            List<String> chain = new ArrayList<>();
            chain.add(current().text());
            advance();
            while (isPunct(current(), ".")
                    && peek(1).type() == ScriptToken.Type.IDENT) {
                chain.add(peek(1).text());
                advance();
                advance();
            }
            // paramètres génériques : listOf<String>(…) — sautés
            if (isPunct(current(), "<")) {
                skipGenericParameters();
            }
            if (!isPunct(current(), "(")) {
                return new ScriptExpr.Reference(chain);
            }
            String name = chain.get(chain.size() - 1);
            List<String> receiver = chain.subList(0, chain.size() - 1);
            List<ScriptExpr> args = arguments();
            // chaîne d'appels : libs.versions.x.get().toInt() devient
            // un appel imbriqué — la structure est préservée
            while (isPunct(current(), ".")
                    && peek(1).type() == ScriptToken.Type.IDENT) {
                List<String> nextReceiver = new ArrayList<>(receiver);
                nextReceiver.add(name);
                name = peek(1).text();
                advance();
                advance();
                if (isPunct(current(), "<")) {
                    skipGenericParameters();
                }
                receiver = nextReceiver;
                args = isPunct(current(), "(") ? arguments()
                        : List.of();
                if (!isPunct(current(), "(")) {
                    break;
                }
            }
            return new ScriptExpr.CallExpr(receiver, name, args);
        }

        /** Saute des paramètres génériques équilibrés {@code <…>}. */
        private void skipGenericParameters() {
            int depth = 0;
            while (current().type() != ScriptToken.Type.EOF) {
                if (isPunct(current(), "<")) {
                    depth++;
                } else if (isPunct(current(), ">")) {
                    depth--;
                    if (depth == 0) {
                        advance();
                        return;
                    }
                } else if (isPunct(current(), ";")
                        || isPunct(current(), "{")) {
                    return;
                }
                advance();
            }
        }

        /**
         * Suit une chaîne après un appel ({@code File('x').text})
         * sans avaler la suite du script : segments pointés et
         * parenthèses équilibrées uniquement.
         */
        private String callChainSuffix() {
            StringBuilder raw = new StringBuilder();
            while (isPunct(current(), ".")
                    && peek(1).type() == ScriptToken.Type.IDENT) {
                raw.append('.').append(peek(1).text());
                advance();
                advance();
                if (isPunct(current(), "(")) {
                    int depth = 0;
                    while (current().type() != ScriptToken.Type.EOF) {
                        if (isPunct(current(), "(")) {
                            depth++;
                        } else if (isPunct(current(), ")")) {
                            depth--;
                        }
                        raw.append(separator(raw))
                                .append(current().text());
                        advance();
                        if (depth == 0) {
                            break;
                        }
                    }
                }
            }
            return raw.toString();
        }

        /** Liste ou table littérale entre crochets. */
        private ScriptExpr listOrMap() {
            advance();
            List<ScriptExpr> items = new ArrayList<>();
            List<ScriptExpr.MapExpr.Entry> entries = new ArrayList<>();
            boolean isMap = false;
            if (isPunct(current(), "]")) {
                advance();
                return new ScriptExpr.ListExpr(items);
            }
            ScriptExpr first = expression();
            if (isPunct(current(), ":")) {
                isMap = true;
                advance();
                entries.add(new ScriptExpr.MapExpr.Entry(first,
                        expression()));
            } else {
                items.add(first);
            }
            while (isPunct(current(), ",")) {
                advance();
                if (isPunct(current(), "]")) {
                    break;
                }
                ScriptExpr key = expression();
                if (isMap && isPunct(current(), ":")) {
                    advance();
                    entries.add(new ScriptExpr.MapExpr.Entry(key,
                            expression()));
                } else if (!isMap && isPunct(current(), ":")) {
                    isMap = true;
                    advance();
                    entries.add(new ScriptExpr.MapExpr.Entry(key,
                            expression()));
                } else {
                    items.add(key);
                }
            }
            expect("]");
            if (isMap) {
                return new ScriptExpr.MapExpr(entries);
            }
            return new ScriptExpr.ListExpr(items);
        }

        /** Entrées nommées Groovy : « cle: valeur, cle2: valeur2 ». */
        private ScriptExpr namedEntries() {
            List<ScriptExpr.MapExpr.Entry> entries = new ArrayList<>();
            while (current().type() == ScriptToken.Type.IDENT
                    && isPunct(peek(1), ":")) {
                String key = current().text();
                advance();
                advance();
                entries.add(new ScriptExpr.MapExpr.Entry(
                        new ScriptExpr.Literal(key,
                                ScriptExpr.Literal.Style.OTHER),
                        expression()));
                if (isPunct(current(), ",")) {
                    advance();
                } else {
                    break;
                }
            }
            return new ScriptExpr.MapExpr(entries);
        }

        /** Instruction non comprise : texte brut jusqu'à une frontière. */
        private ScriptStmt unknown() {
            int line = current().line();
            String raw = rawTail();
            problems.add(Problem.info("script-stmt-unknown",
                    "statement kept raw (line " + line + ")"));
            return ScriptStmt.unknown(raw, line);
        }

        /**
         * Consomme les jetons jusqu'à une frontière d'expression
         * (virgule, parenthèse ou crochet fermant, point-virgule,
         * accolade fermante) — jamais au-delà, pour ne pas avaler la
         * suite du script.
         */
        private String rawTail() {
            StringBuilder raw = new StringBuilder();
            int depth = 0;
            while (current().type() != ScriptToken.Type.EOF) {
                if (depth == 0 && (isPunct(current(), ";")
                        || isPunct(current(), "}")
                        || isPunct(current(), ",")
                        || isPunct(current(), ")")
                        || isPunct(current(), "]"))) {
                    break;
                }
                if (isPunct(current(), "{") || isPunct(current(), "(")
                        || isPunct(current(), "[")) {
                    depth++;
                }
                if (isPunct(current(), "}") || isPunct(current(), ")")
                        || isPunct(current(), "]")) {
                    depth--;
                }
                raw.append(separator(raw)).append(current().text());
                advance();
            }
            return raw.toString();
        }

        private String separator(StringBuilder raw) {
            return raw.isEmpty() ? "" : " ";
        }

        /**
         * Après une instruction : un identifiant ou une accolade
         * ouvrante ouvre l'instruction suivante (Groovy sépare les
         * instructions par des fins de ligne, avalées par le lecteur) ;
         * tout le reste traîne et est sauté avec un problème.
         */
        private void resynchronize() {
            ScriptToken token = current();
            if (token.type() == ScriptToken.Type.EOF
                    || isPunct(token, ";") || isPunct(token, "}")
                    || token.type() == ScriptToken.Type.IDENT
                    || isPunct(token, "{")) {
                return;
            }
            problems.add(Problem.warn("script-token-unexpected",
                    "unexpected token '" + token.text()
                            + "' at line " + token.line()));
            while (current().type() != ScriptToken.Type.EOF
                    && !isPunct(current(), ";")
                    && !isPunct(current(), "}")
                    && current().type() != ScriptToken.Type.IDENT
                    && !isPunct(current(), "{")) {
                advance();
            }
        }

        private void skipSeparators() {
            while (isPunct(current(), ";")) {
                advance();
            }
        }

        private void expect(String punct) {
            if (isPunct(current(), punct)) {
                advance();
                return;
            }
            if (current().type() != ScriptToken.Type.EOF) {
                problems.add(Problem.warn("script-token-expected",
                        "expected '" + punct + "' but found '"
                                + current().text() + "' at line "
                                + current().line()));
            }
        }

        private boolean isLiteral(ScriptToken token) {
            return switch (token.type()) {
                case STRING_DOUBLE, STRING_SINGLE, STRING_TRIPLE_DOUBLE,
                        STRING_TRIPLE_SINGLE, NUMBER -> true;
                default -> false;
            };
        }

        private boolean isPunct(ScriptToken token, String text) {
            return token.type() == ScriptToken.Type.PUNCT
                    && token.text().equals(text);
        }

        private ScriptToken current() {
            return tokens.get(Math.min(index, tokens.size() - 1));
        }

        private ScriptToken peek(int offset) {
            return tokens.get(Math.min(index + offset,
                    tokens.size() - 1));
        }

        private void advance() {
            if (index < tokens.size() - 1) {
                index++;
            }
        }

        private static String join(List<String> chain) {
            return String.join(".", chain);
        }
    }
}
