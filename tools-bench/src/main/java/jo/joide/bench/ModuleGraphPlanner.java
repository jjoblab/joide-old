package jo.joide.bench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Planification déterministe du graphe de modules.
 *
 * <p>Le graphe est en couches numérotées de 0 (base) à {@code layers-1}
 * (sommet). Toute arête va d'une couche vers une couche strictement
 * inférieure : acyclique par construction. Quelques modules « hubs »
 * (couches basses et intermédiaires) concentrent les dépendances — le
 * modèle « quelques modules centraux très dépendus » exigé par le
 * prompt. Chaque module au-dessus de la base dépend d'au moins un
 * module ; les dépendances privilégient la couche immédiatement
 * inférieure et, avec une probabilité forte, le hub le plus proche.</p>
 */
public final class ModuleGraphPlanner {

    /** Un module planifié : nom, couche, dépendances directes, rôle. */
    public static final class PlannedModule {
        /** Nom court du module (ex. « m012 »). */
        public final String name;
        /** Couche : 0 = base, {@code layers-1} = sommet. */
        public final int layer;
        /** Dépendances directes (noms), éventuellement vide à la base. */
        public final List<String> dependencies = new ArrayList<>();
        /** Hub très dépendu (concentre les arêtes entrantes). */
        public final boolean hub;

        PlannedModule(String name, int layer, boolean hub) {
            this.name = name;
            this.layer = layer;
            this.hub = hub;
        }
    }

    private final TierSpec tier;
    private final List<PlannedModule> modules = new ArrayList<>();
    private final Map<Integer, List<PlannedModule>> byLayer = new HashMap<>();

    /**
     * @param tier le tier à planifier
     * @return la liste des modules, ordonnée des couches basses aux
     *         hautes puis par nom (ordre de génération déterministe)
     */
    public static List<PlannedModule> plan(TierSpec tier) {
        return new ModuleGraphPlanner(tier).build();
    }

    private ModuleGraphPlanner(TierSpec tier) {
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    private List<PlannedModule> build() {
        int[] layerSizes = distributeLayers();
        // Hubs : répartis sur les couches basses et intermédiaires.
        Set<String> hubNames = new HashSet<>();
        Random hubRandom = new Random(tier.seed ^ 0x48_5542L);
        int hubsLeft = tier.hubs;
        int hubLayer = 1;
        while (hubsLeft > 0 && hubLayer < tier.layers) {
            List<PlannedModule> candidates = byLayer.get(hubLayer);
            if (candidates != null && !candidates.isEmpty()) {
                PlannedModule chosen =
                        candidates.get(hubRandom.nextInt(candidates.size()));
                if (hubNames.add(chosen.name)) {
                    hubsLeft--;
                }
            }
            hubLayer = (hubLayer + 1) % Math.max(1, tier.layers - 1);
            if (hubLayer == 0) {
                hubLayer = 1;
            }
        }
        // Arêtes : chaque module au-dessus de la base a des dépendances.
        for (PlannedModule module : modules) {
            if (module.layer == 0) {
                continue;
            }
            Random rng = new Random(tier.seed ^ fnv64(module.name));
            List<PlannedModule> lower = byLayer.get(module.layer - 1);
            int depCount = 1 + rng.nextInt(Math.min(3, lower.size()));
            Set<String> chosen = new HashSet<>();
            // Dépendances ordinaires dans la couche immédiatement inférieure.
            for (int i = 0; i < depCount; i++) {
                PlannedModule candidate = lower.get(rng.nextInt(lower.size()));
                chosen.add(candidate.name);
            }
            // Biais « hub » : le hub le plus proche est presque toujours
            // dépendu — c'est lui qui concentre les arêtes entrantes.
            List<PlannedModule> hubs = new ArrayList<>();
            for (int l = module.layer - 1; l >= 0 && hubs.isEmpty(); l--) {
                for (PlannedModule candidate : byLayer.get(l)) {
                    if (candidate.hub) {
                        hubs.add(candidate);
                    }
                }
            }
            if (!hubs.isEmpty() && rng.nextInt(10) < 7) {
                PlannedModule hub = hubs.get(rng.nextInt(hubs.size()));
                chosen.add(hub.name);
            }
            List<String> deps = new ArrayList<>(chosen);
            java.util.Collections.sort(deps);
            module.dependencies.addAll(deps);
        }
        return new ArrayList<>(modules);
    }

    /**
     * Répartition des modules par couche : la base est petite, le sommet
     * est nombreux (poids géométriques), total exact.
     */
    private int[] distributeLayers() {
        double[] weights = new double[tier.layers];
        double total = 0;
        for (int l = 0; l < tier.layers; l++) {
            weights[l] = Math.pow(1.9, l);
            total += weights[l];
        }
        int[] sizes = new int[tier.layers];
        int assigned = 0;
        for (int l = 0; l < tier.layers; l++) {
            sizes[l] = Math.max(1, (int) Math.round(
                    tier.modules * weights[l] / total));
            assigned += sizes[l];
        }
        // Ajuster au total exact : le sommet absorbe l'écart.
        sizes[tier.layers - 1] += tier.modules - assigned;
        if (sizes[tier.layers - 1] < 1) {
            sizes[tier.layers - 1] = 1;
        }
        int index = 0;
        for (int l = 0; l < tier.layers; l++) {
            List<PlannedModule> inLayer = new ArrayList<>();
            for (int i = 0; i < sizes[l]; i++) {
                PlannedModule module = new PlannedModule(
                        String.format(java.util.Locale.ROOT, "m%03d", index),
                        l, false);
                modules.add(module);
                inLayer.add(module);
                index++;
            }
            byLayer.put(l, inLayer);
        }
        return sizes;
    }

    /** Hash FNV-1a 64 bits, stable sur toutes les JVM. */
    public static long fnv64(String text) {
        long hash = 0xcbf2_9ce4_8422_2325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x1000_0000_01b3L;
        }
        return hash;
    }
}
