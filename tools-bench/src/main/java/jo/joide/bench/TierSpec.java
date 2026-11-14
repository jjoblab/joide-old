package jo.joide.bench;

import java.util.Locale;
import java.util.Objects;

/**
 * Paramètres d'un tier de projet synthétique (ADR-014).
 *
 * <p>Les graines sont des constantes commitées : à tier égal, la
 * génération doit reproduire les mêmes octets. Les tailles suivent les
 * exigences du prompt (§7 P0) : S = 5 modules / 500 fichiers,
 * M = 50 / 5 000, L = 200 / 30 000, XL = 500 / 100 000.</p>
 */
public enum TierSpec {

    /** Petit : 5 modules, 500 fichiers, 3 couches, 1 module central. */
    S(5, 500, 3, 1, 101_001L),
    /** Moyen : 50 modules, 5 000 fichiers, 5 couches, 4 modules centraux. */
    M(50, 5_000, 5, 4, 101_002L),
    /** Large : 200 modules, 30 000 fichiers, 6 couches, 10 modules centraux. */
    L(200, 30_000, 6, 10, 101_003L),
    /** Très large : 500 modules, 100 000 fichiers, 7 couches, 20 centraux. */
    XL(500, 100_000, 7, 20, 101_004L);

    /** Nombre de modules du projet généré. */
    public final int modules;
    /** Nombre total de fichiers écrits (tous types confondus). */
    public final int totalFiles;
    /** Nombre de couches du graphe de modules (0 = base). */
    public final int layers;
    /** Nombre de modules « hubs », très dépendus. */
    public final int hubs;
    /** Graine maîtresse du tier (constante commitée). */
    public final long seed;

    TierSpec(int modules, int totalFiles, int layers, int hubs, long seed) {
        this.modules = modules;
        this.totalFiles = totalFiles;
        this.layers = layers;
        this.hubs = hubs;
        this.seed = seed;
    }

    /**
     * @param name S, M, L ou XL (insensible à la casse)
     * @return le tier correspondant
     */
    public static TierSpec parse(String name) {
        Objects.requireNonNull(name, "name");
        return valueOf(name.toUpperCase(Locale.ROOT));
    }

    /** @return le nombre de fichiers sources (85 % du total, ADR-014) */
    public int sourceFiles() {
        return (int) ((long) totalFiles * 85 / 100);
    }

    /** @return le nom canonique du projet généré */
    public String projectName() {
        return "synthetic-" + name().toLowerCase(Locale.ROOT);
    }
}
