package jo.joide.build.daemon.bench;

import java.util.Locale;

/**
 * Tiers du banc du démon : mêmes gabarits et graines que
 * tools-bench ({@code TierSpec}) — le banc du démon génère sa propre
 * charge (sources Java seules : la preuve porte le tas, pas le
 * mélange de langages) mais les comptes par module, couches et
 * modules centraux sont ceux des gabarits de référence.
 */
public enum DaemonTier {

    /** 5 modules, 500 fichiers, 3 couches, 1 module central. */
    S(5, 500, 3, 1, 101_001L),
    /** 50 modules, 5 000 fichiers, 5 couches, 4 modules centraux. */
    M(50, 5_000, 5, 4, 101_002L),
    /** 200 modules, 30 000 fichiers, 6 couches, 10 modules centraux. */
    L(200, 30_000, 6, 10, 101_003L),
    /** 500 modules, 100 000 fichiers, 7 couches, 20 modules centraux. */
    XL(500, 100_000, 7, 20, 101_004L);

    /** Nombre de modules du projet généré. */
    public final int modules;
    /** Nombre total de fichiers écrits. */
    public final int totalFiles;
    /** Nombre de couches du graphe de modules. */
    public final int layers;
    /** Nombre de modules « hubs », très dépendus. */
    public final int hubs;
    /** Graine maîtresse (constante commitée, comme tools-bench). */
    public final long seed;

    DaemonTier(int modules, int totalFiles, int layers, int hubs,
               long seed) {
        this.modules = modules;
        this.totalFiles = totalFiles;
        this.layers = layers;
        this.hubs = hubs;
        this.seed = seed;
    }

    /** @return le nombre de fichiers sources (85 % du total) */
    public int sourceFiles() {
        return (int) ((long) totalFiles * 85 / 100);
    }

    /**
     * @param name S, M, L ou XL (insensible à la casse)
     * @return le tier correspondant
     */
    public static DaemonTier parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
