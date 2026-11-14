package jo.joide.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Génération d'un fichier source Java compilable.
 *
 * <p>Pour rester compilable, un fichier ne référence que : les types de
 * son propre module (même paquet, sans import) et un sous-ensemble de
 * types importés tirés des dépendances directes (registre, contrat fixe
 * de {@link TypeRegistry}). Le nombre de lignes vise la cible tirée
 * (30 à 400), dépassée d'au plus une méthode.</p>
 */
public final class JavaWriter {

    private final String module;
    private final List<TypeRegistry.ExportedType> ownTypes;
    private final List<TypeRegistry.ExportedType> depTypes;

    /**
     * @param registry  registre des types exportés
     * @param module    module pour lequel écrire
     * @param directDeps dépendances directes du module (noms)
     */
    public JavaWriter(TypeRegistry registry, String module,
                      List<String> directDeps) {
        Objects.requireNonNull(registry, "registry");
        this.module = Objects.requireNonNull(module, "module");
        this.ownTypes = new ArrayList<>(registry.exportsOf(module));
        this.depTypes = new ArrayList<>();
        for (String dep : Objects.requireNonNull(directDeps, "directDeps")) {
            depTypes.addAll(registry.exportsOf(dep));
        }
    }

    /**
     * Génère le contenu d'un fichier Java.
     *
     * @param type        type exporté décrit par ce fichier
     * @param targetLines cible de lignes (30 à 400)
     * @param rng         générateur propre au fichier (graine par chemin)
     * @return le contenu complet du fichier, fins de ligne LF
     */
    public String write(TypeRegistry.ExportedType type, int targetLines,
                        Random rng) {
        Objects.requireNonNull(type, "type");
        List<TypeRegistry.ExportedType> imported = pickImports(rng);
        // Pool des références croisées autorisées dans les corps : types
        // importés uniquement (les types du module sont dans le paquet).
        List<TypeRegistry.ExportedType> crossPool = imported;
        StringBuilder out = new StringBuilder(targetLines * 48 + 256);
        out.append("// Fichier genere par tools-bench — ne pas editer.\n");
        out.append("package com.example.").append(module).append(";\n\n");
        for (TypeRegistry.ExportedType importType : imported) {
            out.append("import ").append(importType.fqn).append(";\n");
        }
        if (!imported.isEmpty()) {
            out.append("\n");
        }
        switch (type.kind) {
            case CLASS:
                appendClass(out, type, targetLines, rng, crossPool);
                break;
            case INTERFACE:
                appendInterface(out, type, rng);
                break;
            case ENUM:
                appendEnum(out, type, rng);
                break;
            case RECORD:
                appendRecord(out, type, rng);
                break;
            default:
                throw new IllegalArgumentException("unknown kind: " + type.kind);
        }
        return out.toString();
    }

    private List<TypeRegistry.ExportedType> pickImports(Random rng) {
        List<TypeRegistry.ExportedType> imports = new ArrayList<>();
        int tries = 0;
        int wanted = depTypes.isEmpty() ? 0 : 1 + rng.nextInt(3);
        while (imports.size() < wanted && tries < 10 && !depTypes.isEmpty()) {
            TypeRegistry.ExportedType candidate =
                    depTypes.get(rng.nextInt(depTypes.size()));
            if (!imports.contains(candidate)) {
                imports.add(candidate);
            }
            tries++;
        }
        return imports;
    }

    private void appendClass(StringBuilder out, TypeRegistry.ExportedType type,
                             int targetLines, Random rng,
                             List<TypeRegistry.ExportedType> crossPool) {
        out.append("public class ").append(type.simpleName).append(" {\n\n");
        out.append("    public static final String PREFIX = \"")
                .append(word(rng)).append("\";\n\n");
        out.append("    private final String name;\n\n");
        out.append("    public ").append(type.simpleName)
                .append("(String name) {\n        this.name = name;\n    }\n\n");
        // Contrat fixe du registre (TypeRegistry) : sans ces deux membres,
        // aucune référence croisée générée ne compilerait.
        out.append("    public String process(String input) {\n")
                .append("        return PREFIX + input + name;\n    }\n\n");
        out.append("    public static int counter(int n) {\n")
                .append("        return (n * 31 + 17) & 0x7fffffff;\n    }\n\n");
        int methodIndex = 0;
        while (countLines(out) < targetLines && methodIndex < 200) {
            appendMethod(out, rng, crossPool, methodIndex);
            methodIndex++;
        }
        out.append("}\n");
    }

    private void appendMethod(StringBuilder out, Random rng,
                              List<TypeRegistry.ExportedType> crossPool,
                              int index) {
        out.append("    public String render").append(index)
                .append("(int count) {\n");
        out.append(body(rng, 3 + rng.nextInt(8), crossPool));
        out.append("        return PREFIX + count + name;\n    }\n\n");
    }

    /** Corps de méthode : appels au contrat fixe du registre. */
    private String body(Random rng, int statements,
                        List<TypeRegistry.ExportedType> crossPool) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < statements; i++) {
            if (crossPool.isEmpty()) {
                sb.append("        count += ").append(rng.nextInt(97)).append(";\n");
                continue;
            }
            TypeRegistry.ExportedType ref =
                    crossPool.get(rng.nextInt(crossPool.size()));
            switch (ref.kind) {
                case CLASS:
                    if (rng.nextBoolean()) {
                        sb.append("        count += ").append(ref.simpleName)
                                .append(".counter(count);\n");
                    } else {
                        sb.append("        String part").append(i)
                                .append(" = new ").append(ref.simpleName)
                                .append("(\"").append(word(rng))
                                .append("\").process(name);\n");
                    }
                    break;
                case ENUM:
                    sb.append("        count += ").append(ref.simpleName)
                            .append(".")
                            .append(ref.enumValues.get(
                                    rng.nextInt(ref.enumValues.size())))
                            .append(".ordinal() + 1;\n");
                    break;
                case RECORD:
                    sb.append("        String part").append(i)
                            .append(" = new ").append(ref.simpleName)
                            .append("(name, count).first();\n");
                    break;
                case INTERFACE:
                    sb.append("        count += ").append(ref.simpleName)
                            .append(".HASH_").append(ref.simpleName)
                            .append(";\n");
                    break;
                default:
                    sb.append("        count += 1;\n");
            }
        }
        sb.append("        if (count < 0) {\n            return name;\n        }\n");
        return sb.toString();
    }

    private void appendInterface(StringBuilder out,
                                 TypeRegistry.ExportedType type,
                                 Random rng) {
        out.append("public interface ").append(type.simpleName).append(" {\n\n");
        out.append("    int HASH_").append(type.simpleName).append(" = ")
                .append(rng.nextInt(1000)).append(";\n\n");
        out.append("    String name();\n");
        out.append("}\n");
    }

    private void appendEnum(StringBuilder out, TypeRegistry.ExportedType type,
                            Random rng) {
        out.append("public enum ").append(type.simpleName).append(" {\n    ");
        for (int i = 0; i < type.enumValues.size(); i++) {
            if (i > 0) {
                out.append(",\n    ");
            }
            out.append(type.enumValues.get(i));
        }
        out.append(";\n\n    public int weight() {\n        return ordinal() + ")
                .append(1 + rng.nextInt(9)).append(";\n    }\n");
        out.append("}\n");
    }

    private void appendRecord(StringBuilder out, TypeRegistry.ExportedType type,
                              Random rng) {
        out.append("public record ").append(type.simpleName)
                .append("(String first, int second) {\n\n");
        out.append("    public String label() {\n        return first + \"#\" + second;\n    }\n");
        out.append("}\n");
    }

    private static int countLines(StringBuilder sb) {
        int lines = 0;
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static String word(Random rng) {
        char[] chars = new char[3 + rng.nextInt(5)];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ('a' + rng.nextInt(26));
        }
        return new String(chars);
    }
}
