package jo.joide.imports.gradle.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jo.joide.platform.Problem;

/**
 * Un script Gradle analysé : ses instructions dans l'ordre et les
 * problèmes rencontrés. Immuable.
 *
 * <p>Le script est une suite plate d'instructions ; les blocs en
 * fermeture sont portés par leurs instructions d'appel. Les méthodes
 * d'accès par nom sont des commodités pour les lecteurs — aucune
 * sémantique Gradle ne vit ici (ADR-039).</p>
 */
public final class Script {

    private final List<ScriptStmt> statements;
    private final List<Problem> problems;

    /**
     * @param statements instructions dans l'ordre du script
     * @param problems   problèmes d'analyse (lexique, syntaxe)
     */
    Script(List<ScriptStmt> statements, List<Problem> problems) {
        this.statements = List.copyOf(statements);
        this.problems = List.copyOf(problems);
    }

    /** @return les instructions dans l'ordre */
    public List<ScriptStmt> statements() {
        return statements;
    }

    /** @return les problèmes d'analyse */
    public List<Problem> problems() {
        return problems;
    }

    /** @return les appels, dans l'ordre */
    public List<ScriptStmt> calls() {
        List<ScriptStmt> calls = new ArrayList<>();
        for (ScriptStmt stmt : statements) {
            if (stmt.kind() == ScriptStmt.Kind.CALL) {
                calls.add(stmt);
            }
        }
        return calls;
    }

    /**
     * @param qualifiedName nom qualifié (« android », «
     *                      tasks.register »)
     * @return le premier appel de ce nom, s'il existe
     */
    public Optional<ScriptStmt> findCall(String qualifiedName) {
        for (ScriptStmt stmt : statements) {
            if (stmt.isCall(qualifiedName)) {
                return Optional.of(stmt);
            }
        }
        return Optional.empty();
    }

    /**
     * @return les affectations par chaîne cible ; la dernière gagne
     *         (comme en Groovy, la dernière écriture l'emporte)
     */
    public Map<String, ScriptStmt> assignments() {
        Map<String, ScriptStmt> byTarget = new LinkedHashMap<>();
        for (ScriptStmt stmt : statements) {
            if (stmt.kind() == ScriptStmt.Kind.ASSIGN) {
                byTarget.put(stmt.target(), stmt);
            }
        }
        return byTarget;
    }

    /**
     * @param target chaîne cible (« namespace », « rootProject.name »)
     * @return l'affectation correspondante, s'il existe
     */
    public Optional<ScriptStmt> findAssign(String target) {
        for (ScriptStmt stmt : statements) {
            if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                    && target.equals(stmt.target())) {
                return Optional.of(stmt);
            }
        }
        return Optional.empty();
    }

    /**
     * @param target chaîne cible
     * @return la valeur affectée comme texte, si c'est une littérale
     */
    public Optional<String> findString(String target) {
        return findAssign(target).map(ScriptStmt::value)
                .flatMap(ScriptStmt::asString);
    }

    /**
     * Cherche une propriété écrite soit par affectation
     * ({@code namespace = "x"}), soit par appel à un argument
     * ({@code minSdk 24}) — les deux dialectes écrivent les mêmes
     * réglages de ces deux manières.
     *
     * @param name nom de la propriété, sans récepteur
     * @return la valeur textuelle, si elle est une littérale
     */
    public Optional<String> findProperty(String name) {
        Optional<String> assigned = findString(name);
        if (assigned.isPresent()) {
            return assigned;
        }
        for (ScriptStmt stmt : statements) {
            if (stmt.isCall(name) && stmt.args().size() == 1) {
                Optional<String> value = ScriptStmt.asString(
                        stmt.args().get(0));
                if (value.isPresent()) {
                    return value;
                }
            }
        }
        return Optional.empty();
    }

    /** @return une description courte, pour les rapports */
    @Override
    public String toString() {
        return "Script[" + statements.size() + " statements, "
                + problems.size() + " problems]";
    }

    /**
     * @param other objet comparé
     * @return égalité structurelle (instructions et problèmes)
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Script that)) {
            return false;
        }
        return statements.equals(that.statements)
                && problems.equals(that.problems);
    }

    @Override
    public int hashCode() {
        return statements.hashCode() * 31 + problems.hashCode();
    }
}
