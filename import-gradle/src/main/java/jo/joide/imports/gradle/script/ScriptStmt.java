package jo.joide.imports.gradle.script;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Une instruction de script Gradle, en forme tolérante : appel de
 * méthode (avec ou sans bloc en fermeture), affectation (avec ou sans
 * opérateur composé) ou instruction non comprise.
 *
 * <p>L'instruction garde sa ligne d'origine pour les rapports. Elle
 * ne capture pas la sémantique de Gradle — c'est l'affaire des
 * lecteurs (ADR-039).</p>
 */
public final class ScriptStmt {

    /** Nature de l'instruction. */
    public enum Kind {
        /** Appel de méthode. */
        CALL,
        /** Affectation d'une propriété. */
        ASSIGN,
        /** Instruction non comprise, conservée brute. */
        UNKNOWN
    }

    private final Kind kind;
    private final List<String> receiver;
    private final String name;
    private final String target;
    private final String operator;
    private final List<ScriptExpr> args;
    private final Script block;
    private final ScriptExpr value;
    private final String raw;
    private final int line;

    private ScriptStmt(Kind kind, List<String> receiver, String name,
                       String target, String operator,
                       List<ScriptExpr> args, Script block,
                       ScriptExpr value, String raw, int line) {
        this.kind = kind;
        this.receiver = List.copyOf(receiver);
        this.name = name;
        this.target = target;
        this.operator = operator;
        this.args = List.copyOf(args);
        this.block = block;
        this.value = value;
        this.raw = raw;
        this.line = line;
    }

    /** @return un appel, avec ou sans arguments et bloc. */
    static ScriptStmt call(List<String> receiver, String name,
                           List<ScriptExpr> args, Script block, int line) {
        return new ScriptStmt(Kind.CALL, receiver, name, null, null,
                args, block, null, null, line);
    }

    /** @return une affectation, opérateur « = » ou composé (« += »). */
    static ScriptStmt assign(String target, String operator,
                             ScriptExpr value, int line) {
        return new ScriptStmt(Kind.ASSIGN, List.of(), null, target,
                operator, List.of(), null, value, null, line);
    }

    /** @return une instruction non comprise, conservée brute. */
    static ScriptStmt unknown(String raw, int line) {
        return new ScriptStmt(Kind.UNKNOWN, List.of(), null, null, null,
                List.of(), null, null, raw, line);
    }

    /** @return la nature de l'instruction */
    public Kind kind() {
        return kind;
    }

    /** @return l'appel : segments avant le nom, éventuellement vides */
    public List<String> receiver() {
        return receiver;
    }

    /** @return l'appel : nom de la méthode, ou {@code null} */
    public String name() {
        return name;
    }

    /**
     * @return l'appel qualifié (« tasks.register », « include »),
     *         ou une chaîne vide
     */
    public String qualifiedName() {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String segment : receiver) {
            out.append(segment).append('.');
        }
        return out.append(name).toString();
    }

    /** @return l'affectation : chaîne cible (« a.b.c »), ou {@code null} */
    public String target() {
        return target;
    }

    /** @return l'affectation : opérateur (« = », « += »), ou {@code null} */
    public String operator() {
        return operator;
    }

    /** @return l'appel : arguments */
    public List<ScriptExpr> args() {
        return args;
    }

    /** @return l'appel : bloc en fermeture, ou {@code null} */
    public Script block() {
        return block;
    }

    /** @return l'affectation : valeur, ou {@code null} */
    public ScriptExpr value() {
        return value;
    }

    /** @return l'instruction non comprise : texte brut, ou {@code null} */
    public String raw() {
        return raw;
    }

    /** @return la ligne d'origine, à partir de 1 */
    public int line() {
        return line;
    }

    /**
     * @param qualifiedName nom qualifié cherché
     * @return vrai si c'est un appel de ce nom qualifié
     */
    public boolean isCall(String qualifiedName) {
        return kind == Kind.CALL
                && qualifiedName.equals(qualifiedName());
    }

    /**
     * @return le premier argument comme chaîne décodée, s'il existe
     *         et est une littérale
     */
    public Optional<String> firstStringArgument() {
        if (kind != Kind.CALL || args.isEmpty()) {
            return Optional.empty();
        }
        return asString(args.get(0));
    }

    /**
     * @param expr expression à lire
     * @return la valeur textuelle si l'expression est une littérale
     */
    public static Optional<String> asString(ScriptExpr expr) {
        if (expr instanceof ScriptExpr.Literal literal) {
            return Optional.of(literal.decoded());
        }
        return Optional.empty();
    }

    /** @return une description courte, pour les rapports */
    @Override
    public String toString() {
        return switch (kind) {
            case CALL -> "call " + qualifiedName() + " (" + line + ")";
            case ASSIGN -> "assign " + target + " (" + line + ")";
            case UNKNOWN -> "unknown (" + line + ")";
        };
    }

    /**
     * @param other objet comparé
     * @return égalité structurelle (lignes exclues)
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScriptStmt that)) {
            return false;
        }
        return kind == that.kind && receiver.equals(that.receiver)
                && Objects.equals(name, that.name)
                && Objects.equals(target, that.target)
                && Objects.equals(operator, that.operator)
                && args.equals(that.args)
                && Objects.equals(block, that.block)
                && Objects.equals(value, that.value)
                && Objects.equals(raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, receiver, name, target, operator,
                args, block, value, raw);
    }
}
