package jo.joide.bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Registre des types exportés par chaque module.
 *
 * <p>Chaque fichier Java d'un module décrit exactement un type public :
 * le nombre de types exportés d'un module est donc son budget de
 * fichiers Java. Pour que le code généré soit compilable, ces types
 * suivent un contrat fixe : une classe expose toujours
 * {@code process(String)} et {@code counter(int)} ; un record expose
 * toujours {@code first()} et {@code second()} ; une énumération expose
 * ses valeurs ; une interface sert de type et expose une constante
 * {@code HASH_<Nom>}. Un fichier ne référence que les types de son
 * module ou de ses dépendances directes.</p>
 */
public final class TypeRegistry {

    /** Genre de type exporté. */
    public enum Kind { CLASS, INTERFACE, ENUM, RECORD }

    /** Un type exporté par un module du projet synthétique. */
    public static final class ExportedType {
        /** Nom canonique (ex. « com.example.m003.Alpha1 »). */
        public final String fqn;
        /** Nom simple. */
        public final String simpleName;
        /** Genre du type. */
        public final Kind kind;
        /** Valeurs d'énumération (ENUM seulement). */
        public final List<String> enumValues = new ArrayList<>();

        ExportedType(String fqn, String simpleName, Kind kind) {
            this.fqn = fqn;
            this.simpleName = simpleName;
            this.kind = kind;
        }
    }

    /** Noms simples possibles (identifiants anglais, D3). */
    private static final String[] WORDS = {
            "Alpha", "Bravo", "Cache", "Delta", "Entry", "Focus", "Gate",
            "Hook", "Index", "Joint", "Keeper", "Ledger", "Marker", "Node",
            "Omega", "Parser", "Queue", "Rack", "Signal", "Tracker",
            "Union", "Vault", "Wired", "Sector", "Relay", "Beacon",
            "Cable", "Dock", "Beam", "Pillar", "Store", "Router",
    };

    private final Map<String, List<ExportedType>> byModule =
            new HashMap<>();

    /**
     * Construit le registre.
     *
     * @param modules     modules planifiés, ordonnés des couches basses
     *                    aux hautes puis par nom
     * @param javaBudgets nombre de fichiers Java par module, même ordre
     */
    public TypeRegistry(List<ModuleGraphPlanner.PlannedModule> modules,
                        int[] javaBudgets) {
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(javaBudgets, "javaBudgets");
        if (modules.size() != javaBudgets.length) {
            throw new IllegalArgumentException(
                    "module count and java budget count differ");
        }
        int typeIndex = 0;
        for (int m = 0; m < modules.size(); m++) {
            ModuleGraphPlanner.PlannedModule module = modules.get(m);
            List<ExportedType> exported = new ArrayList<>();
            Random rng = new Random(
                    ModuleGraphPlanner.fnv64("types:" + module.name)
                            ^ 0x54_5950_45L);
            for (int i = 0; i < javaBudgets[m]; i++) {
                String simple = WORDS[typeIndex % WORDS.length]
                        + (typeIndex / WORDS.length + 1);
                typeIndex++;
                Kind kind = pickKind(rng);
                ExportedType type = new ExportedType(
                        "com.example." + module.name + "." + simple,
                        simple, kind);
                if (kind == Kind.ENUM) {
                    int values = 2 + rng.nextInt(3);
                    for (int v = 0; v < values; v++) {
                        type.enumValues.add("V" + v);
                    }
                }
                exported.add(type);
            }
            byModule.put(module.name, exported);
        }
    }

    private static Kind pickKind(Random rng) {
        int draw = rng.nextInt(20);
        if (draw < 12) {
            return Kind.CLASS;
        }
        if (draw < 16) {
            return Kind.INTERFACE;
        }
        if (draw < 18) {
            return Kind.ENUM;
        }
        return Kind.RECORD;
    }

    /**
     * @param module nom du module
     * @return les types exportés (non modifiables, ordre stable)
     */
    public List<ExportedType> exportsOf(String module) {
        List<ExportedType> types = byModule.get(module);
        if (types == null) {
            throw new IllegalArgumentException("unknown module: " + module);
        }
        return Collections.unmodifiableList(types);
    }
}
