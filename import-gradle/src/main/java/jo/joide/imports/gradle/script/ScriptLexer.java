package jo.joide.imports.gradle.script;

import java.util.ArrayList;
import java.util.List;
import jo.joide.platform.Problem;

/**
 * Lecteur lexical tolérant des scripts Gradle (Groovy ou Kotlin DSL).
 *
 * <p>Ne lève jamais : une chaîne non fermée, un commentaire non
 * terminé ou un caractère inconnu produit un jeton au mieux et un
 * problème ; la lecture continue. Le lecteur ne connaît ni la
 * sémantique de Gradle, ni celle de Groovy ou de Kotlin — seulement
 * les formes lexicales communes (ADR-039).</p>
 */
final class ScriptLexer {

    private final String source;
    private final List<Problem> problems;
    private final List<ScriptToken> tokens = new ArrayList<>();
    private int pos;
    private int line = 1;

    /**
     * @param source   texte du script (jamais {@code null})
     * @param problems réceptacle des problèmes d'analyse
     */
    ScriptLexer(String source, List<Problem> problems) {
        this.source = source;
        this.problems = problems;
    }

    /** @return tous les jetons, terminés par un jeton de fin. */
    List<ScriptToken> tokens() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
                pos++;
            } else if (c == '/' && ahead(1) == '/') {
                skipLineComment();
            } else if (c == '/' && ahead(1) == '*') {
                skipBlockComment();
            } else if (c == '#') {
                skipLineComment();
            } else if (c == '"' || c == '\'') {
                readString(c);
            } else if (c == '`') {
                readBacktickIdent();
            } else if (Character.isJavaIdentifierStart(c)) {
                readIdent();
            } else if (Character.isDigit(c)) {
                readNumber();
            } else {
                tokens.add(new ScriptToken(ScriptToken.Type.PUNCT,
                        String.valueOf(c), line));
                pos++;
            }
        }
        tokens.add(new ScriptToken(ScriptToken.Type.EOF, "", line));
        return tokens;
    }

    private char ahead(int offset) {
        int index = pos + offset;
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private void skipLineComment() {
        while (pos < source.length() && source.charAt(pos) != '\n') {
            pos++;
        }
    }

    private void skipBlockComment() {
        int startLine = line;
        pos += 2;
        while (pos < source.length()
                && !(source.charAt(pos) == '*'
                        && ahead(1) == '/')) {
            if (source.charAt(pos) == '\n') {
                line++;
            }
            pos++;
        }
        if (pos >= source.length()) {
            problems.add(Problem.warn("script-comment-unterminated",
                    "block comment not closed (line " + startLine + ")"));
            return;
        }
        pos += 2;
    }

    private void readString(char quote) {
        int startLine = line;
        boolean triple = ahead(1) == quote && ahead(2) == quote;
        ScriptToken.Type type = triple
                ? (quote == '"' ? ScriptToken.Type.STRING_TRIPLE_DOUBLE
                        : ScriptToken.Type.STRING_TRIPLE_SINGLE)
                : (quote == '"' ? ScriptToken.Type.STRING_DOUBLE
                        : ScriptToken.Type.STRING_SINGLE);
        pos += triple ? 3 : 1;
        StringBuilder text = new StringBuilder();
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (triple && c == quote && ahead(1) == quote
                    && ahead(2) == quote) {
                pos += 3;
                tokens.add(new ScriptToken(type, text.toString(), startLine));
                return;
            }
            if (!triple && c == quote) {
                pos++;
                tokens.add(new ScriptToken(type, text.toString(), startLine));
                return;
            }
            if (c == '\\' && pos + 1 < source.length()) {
                text.append(c).append(source.charAt(pos + 1));
                pos += 2;
                continue;
            }
            if (c == '\n' && !triple) {
                problems.add(Problem.warn("script-string-unterminated",
                        "string literal not closed (line " + startLine + ")"));
                tokens.add(new ScriptToken(type, text.toString(), startLine));
                return;
            }
            if (c == '\n') {
                line++;
            }
            text.append(c);
            pos++;
        }
        problems.add(Problem.warn("script-string-unterminated",
                "string literal not closed (line " + startLine + ")"));
        tokens.add(new ScriptToken(type, text.toString(), startLine));
    }

    private void readBacktickIdent() {
        int startLine = line;
        pos++;
        StringBuilder text = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '`'
                && source.charAt(pos) != '\n') {
            text.append(source.charAt(pos));
            pos++;
        }
        if (pos < source.length() && source.charAt(pos) == '`') {
            pos++;
        } else {
            problems.add(Problem.warn("script-ident-unterminated",
                    "backtick identifier not closed (line " + startLine + ")"));
        }
        tokens.add(new ScriptToken(ScriptToken.Type.IDENT,
                text.toString(), startLine));
    }

    private void readIdent() {
        int start = pos;
        while (pos < source.length()
                && (Character.isJavaIdentifierPart(source.charAt(pos))
                        || source.charAt(pos) == '$')) {
            pos++;
        }
        tokens.add(new ScriptToken(ScriptToken.Type.IDENT,
                source.substring(start, pos), line));
    }

    private void readNumber() {
        int start = pos;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isDigit(c) || c == '.'
                    || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        tokens.add(new ScriptToken(ScriptToken.Type.NUMBER,
                source.substring(start, pos), line));
    }
}
