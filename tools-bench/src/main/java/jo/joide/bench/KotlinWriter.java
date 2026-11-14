package jo.joide.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Génération d'un fichier source Kotlin.
 *
 * <p>Grammaire volontairement restreinte (classes, data classes, objets
 * compagnons, const, inline, sealed) : ces fichiers ne sont pas compilés
 * (kotlinc hors périmètre de ce module) : elles servent de matière
 * première aux tests différentiels du pipeline JVM. Le contenu reste
 * déterministe à graine
 * égale, comme tout le générateur.</p>
 */
public final class KotlinWriter {

    private final String module;
    private final List<TypeRegistry.ExportedType> depTypes;

    /**
     * @param registry   registre des types exportés
     * @param module     module pour lequel écrire
     * @param directDeps dépendances directes du module (noms)
     */
    public KotlinWriter(TypeRegistry registry, String module,
                        List<String> directDeps) {
        Objects.requireNonNull(registry, "registry");
        this.module = Objects.requireNonNull(module, "module");
        this.depTypes = new ArrayList<>();
        for (String dep : Objects.requireNonNull(directDeps, "directDeps")) {
            depTypes.addAll(registry.exportsOf(dep));
        }
    }

    /**
     * Génère le contenu d'un fichier Kotlin.
     *
     * @param index  index du fichier dans le module
     * @param rng    générateur propre au fichier (graine par chemin)
     * @return le contenu complet du fichier, fins de ligne LF
     */
    public String write(int index, Random rng) {
        TypeRegistry.ExportedType cross =
                depTypes.isEmpty() ? null
                        : depTypes.get(rng.nextInt(depTypes.size()));
        StringBuilder out = new StringBuilder(1024);
        out.append("// Fichier genere par tools-bench — ne pas editer.\n");
        out.append("package com.example.").append(module).append("\n\n");
        if (cross != null) {
            out.append("import ").append(cross.fqn).append("\n\n");
        }
        out.append("const val MAGIC_").append(index).append(" = ")
                .append(rng.nextInt(1000)).append("\n\n");
        out.append("data class Model").append(index)
                .append("(val id: String, val count: Int) {\n");
        out.append("    fun describe(): String = \"$id:$count\"\n");
        out.append("}\n\n");
        if (cross != null && cross.kind == TypeRegistry.Kind.CLASS) {
            out.append("class Service").append(index)
                    .append("(private val api: ").append(cross.simpleName)
                    .append(") {\n");
            out.append("    companion object {\n")
                    .append("        const val LIMIT = ")
                    .append(1 + rng.nextInt(64)).append("\n")
                    .append("    }\n");
            out.append("    fun run(input: String): String {\n");
            out.append("        val tagged = \"[${input.trim()}]\"\n");
            out.append("        return api.process(tagged) + LIMIT\n");
            out.append("    }\n");
            out.append("}\n\n");
        } else {
            out.append("class Service").append(index)
                    .append("(private val seed: Int) {\n");
            out.append("    fun run(input: String): String = \"$seed:$input\"\n");
            out.append("}\n\n");
        }
        out.append("inline fun tag").append(index)
                .append("(value: String): String = \"<${value}>\"\n");
        return out.toString();
    }
}
